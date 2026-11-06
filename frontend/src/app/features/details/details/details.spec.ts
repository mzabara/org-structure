import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideTaiga } from '@taiga-ui/core';
import { Details } from './details';
import type { Employee } from '../../../core/api/org-api';

const employee: Employee = {
  id: 23, fullName: 'Орехова Юлия Дмитриевна', position: 'UX/UI-дизайнер',
  email: 'orehova.y@example.test', department: { id: 9, code: 'PRD-DS', name: 'Дизайн' },
  lineManager: null, dottedManagers: [], directReports: [], assignments: [],
};

describe('Details', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    // jsdom не реализует media queries, необходимые инициализации темы Taiga.
    vi.stubGlobal('matchMedia', (query: string): MediaQueryList => ({
      matches: false, media: query, onchange: null,
      addListener: vi.fn(), removeListener: vi.fn(),
      addEventListener: vi.fn(), removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(() => true),
    }));
    TestBed.configureTestingModule({ imports: [Details],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), provideTaiga()] });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    http.verify();
  });

  it('сохраняет пустые блоки задач и ответственности и ссылку на отдел', async () => {
    const fixture = TestBed.createComponent(Details);
    fixture.componentRef.setInput('mode', 'employees');
    fixture.componentRef.setInput('entityId', 23);
    await fixture.whenStable();
    http.expectOne('/api/v1/employees/23').flush(employee);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Чем занимается');
    expect(element.textContent).toContain('За что отвечает');
    expect(element.textContent?.match(/Не указано/g)?.length).toBeGreaterThanOrEqual(2);
    expect(element.querySelector('a[href="/departments/9"]')).not.toBeNull();
  });

  it('показывает ошибку API и успешно повторяет запрос', async () => {
    const fixture = TestBed.createComponent(Details);
    fixture.componentRef.setInput('mode', 'employees');
    fixture.componentRef.setInput('entityId', 23);
    await fixture.whenStable();
    http.expectOne('/api/v1/employees/23').error(new ProgressEvent('error'));
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Сервер недоступен');
    fixture.componentInstance.load();
    http.expectOne('/api/v1/employees/23').flush(employee);
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(employee.fullName);
  });

  it('разрешает переключить сотрудников отдела на всё поддерево', async () => {
    const fixture = TestBed.createComponent(Details);
    fixture.componentRef.setInput('mode', 'departments');
    fixture.componentRef.setInput('entityId', 2);
    await fixture.whenStable();
    http.expectOne('/api/v1/departments/2').flush({
      id: 2, code: 'DEV', name: 'Разработка', children: [],
      employeeCount: 1, totalEmployeeCount: 14,
    });
    http.expectOne(request => request.url === '/api/v1/departments/2/employees'
      && request.params.get('includeSubdepartments') === 'false').flush({
        items: [], page: 0, size: 20, totalItems: 0, totalPages: 0,
      });
    await fixture.whenStable();
    const toggle = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('[role="switch"]')!;
    expect(toggle.disabled).toBe(false);
    toggle.click();
    await fixture.whenStable();
    http.expectOne(request => request.url === '/api/v1/departments/2/employees'
      && request.params.get('includeSubdepartments') === 'true').flush({
        items: [employee], page: 0, size: 20, totalItems: 1, totalPages: 1,
      });
    await fixture.whenStable();
    expect(toggle.checked).toBe(true);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(employee.fullName);
  });
});
