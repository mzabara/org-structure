import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Search } from './search';

describe('Search', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({ imports: [Search], providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => { http.verify(); vi.useRealTimers(); });

  it('не ищет по одному символу и ждёт 300 мс', async () => {
    const fixture = TestBed.createComponent(Search);
    fixture.componentInstance.update('Б');
    await vi.advanceTimersByTimeAsync(500);
    http.expectNone(req => req.url === '/api/v1/search');
    fixture.componentInstance.update('Белова');
    await vi.advanceTimersByTimeAsync(299);
    http.expectNone(req => req.url === '/api/v1/search');
    await vi.advanceTimersByTimeAsync(1);
    const request = http.expectOne(req => req.url === '/api/v1/search');
    expect(request.request.params.get('q')).toBe('Белова');
    request.flush({ employees: [], departments: [] });
    expect(fixture.componentInstance.state()?.status).toBe('success');
  });

  it('отменяет устаревший HTTP-запрос при новом вводе', async () => {
    const fixture = TestBed.createComponent(Search);
    fixture.componentInstance.update('Белова');
    await vi.advanceTimersByTimeAsync(300);
    const old = http.expectOne(req => req.url === '/api/v1/search');
    fixture.componentInstance.update('Поддержка');
    expect(old.cancelled).toBe(true);
    await vi.advanceTimersByTimeAsync(300);
    http.expectOne(req => req.url === '/api/v1/search').flush({ employees: [], departments: [] });
  });
});
