package com.laimengchao.sso.ehr.module.employee.service;

import com.laimengchao.sso.ehr.module.employee.dto.EmployeeProfileDtos.BankRequest;
import com.laimengchao.sso.ehr.module.employee.dto.EmployeeProfileDtos.EducationRequest;
import com.laimengchao.sso.ehr.module.employee.dto.EmployeeProfileDtos.FamilyRequest;
import com.laimengchao.sso.ehr.module.employee.dto.EmployeeProfileDtos.WorkRequest;
import com.laimengchao.sso.ehr.module.employee.vo.EmployeeProfileVO;

public interface EmployeeProfileService {

    EmployeeProfileVO profile(Long employeeId);

    EmployeeProfileVO.BankVO addBank(Long employeeId, BankRequest req);

    EmployeeProfileVO.BankVO updateBank(Long employeeId, Long id, BankRequest req);

    void deleteBank(Long employeeId, Long id);

    EmployeeProfileVO.EducationVO addEducation(Long employeeId, EducationRequest req);

    EmployeeProfileVO.EducationVO updateEducation(Long employeeId, Long id, EducationRequest req);

    void deleteEducation(Long employeeId, Long id);

    EmployeeProfileVO.WorkVO addWork(Long employeeId, WorkRequest req);

    EmployeeProfileVO.WorkVO updateWork(Long employeeId, Long id, WorkRequest req);

    void deleteWork(Long employeeId, Long id);

    EmployeeProfileVO.FamilyVO addFamily(Long employeeId, FamilyRequest req);

    EmployeeProfileVO.FamilyVO updateFamily(Long employeeId, Long id, FamilyRequest req);

    void deleteFamily(Long employeeId, Long id);
}
