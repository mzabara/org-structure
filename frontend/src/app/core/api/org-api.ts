import { inject, Service } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import type { components } from './schema';

export type Department = components['schemas']['Department'];
export type Employee = components['schemas']['Employee'];
export type DepartmentTree = components['schemas']['DepartmentTreeNode'];
export type EmployeeTree = components['schemas']['EmployeeTreeNode'];
export type EmployeePage = components['schemas']['EmployeePage'];
export type EmployeeSummary = components['schemas']['EmployeeSummary'];
export type SearchResult = components['schemas']['SearchResult'];
export type Assignment = components['schemas']['Assignment'];
export type Relation = components['schemas']['ReportingRelation'];
export type ViewMode = 'departments' | 'employees';

/** Единственный HTTP-клиент использует типы исходного OpenAPI и относительный dev-прокси. */
@Service()
export class OrgApi {
  private readonly http = inject(HttpClient);
  departmentTree() { return this.http.get<DepartmentTree>('/api/v1/departments/tree'); }
  employeeTree() { return this.http.get<EmployeeTree>('/api/v1/employees/tree'); }
  department(id: number) { return this.http.get<Department>(`/api/v1/departments/${id}`); }
  employee(id: number) { return this.http.get<Employee>(`/api/v1/employees/${id}`); }
  departmentEmployees(id: number, includeSubdepartments: boolean, page: number) {
    return this.http.get<EmployeePage>(`/api/v1/departments/${id}/employees`, {
      params: { includeSubdepartments, page, size: 10 },
    });
  }
  search(q: string) { return this.http.get<SearchResult>('/api/v1/search', { params: { q, limit: 10 } }); }
  managementChain(id: number) {
    return this.http.get<EmployeeSummary[]>(`/api/v1/employees/${id}/management-chain`);
  }
  reports(id: number, type: 'LINE' | 'DOTTED' | 'ALL' = 'ALL') {
    return this.http.get<Relation[]>(`/api/v1/employees/${id}/direct-reports`, { params: { type } });
  }
  assignments(id: number) { return this.http.get<Assignment[]>(`/api/v1/employees/${id}/assignments`); }
}
