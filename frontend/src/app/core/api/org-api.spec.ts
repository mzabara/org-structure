import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { OrgApi } from './org-api';

describe('OrgApi', () => {
  let api: OrgApi;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(OrgApi);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('использует относительный API без секретного ключа', () => {
    api.departmentTree().subscribe();
    const request = http.expectOne('/api/v1/departments/tree');
    expect(request.request.method).toBe('GET');
    expect(request.request.headers.has('X-API-Key')).toBe(false);
    request.flush({ id: 1, code: 'EXEC', name: 'Генеральная дирекция', employeeCount: 1, children: [] });
  });

  it('передаёт пагинацию и включение вложенных подразделений', () => {
    api.departmentEmployees(2, true, 1).subscribe();
    const request = http.expectOne(req => req.url === '/api/v1/departments/2/employees');
    expect(request.request.params.get('includeSubdepartments')).toBe('true');
    expect(request.request.params.get('page')).toBe('1');
    expect(request.request.params.get('size')).toBe('10');
    request.flush({ items: [], page: 1, size: 10, totalItems: 0, totalPages: 0 });
  });
});
