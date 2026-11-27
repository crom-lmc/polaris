-- 采购计划归属组织回填（幂等）
-- 背景：采购计划新建时前端不传 orgCode、后端未兜底，导致 org_code 落空串；
--       查询用 org_code LIKE 'scopeCode%' 过滤，空值被过滤掉，列表查不到。
-- 口径：按申请人姓名在 EHR 反查在职员工所属组织编码（与 myProfile 同口径）。
-- 只回填 org_code 为空/NULL 的历史数据，不影响已有归属。
-- 执行前建议先 SELECT 核对；依赖同实例 ehr 库，若分库需先导出映射再更新。

UPDATE polaris_contract.contract_purchase_plan p
JOIN (
    SELECT e.name AS applicant, n.code AS org_code
    FROM ehr.ehr_employee e
    JOIN ehr.ehr_org_node n ON n.id = e.org_id
    WHERE e.deleted = 0 AND e.status <> 60 AND e.account_id IS NOT NULL
    GROUP BY e.name, n.code
) m ON m.applicant = p.applicant
SET p.org_code = m.org_code,
    p.updated_at = NOW()
WHERE (p.org_code IS NULL OR p.org_code = '')
  AND p.applicant IS NOT NULL
  AND p.applicant <> '';

-- 核对：应无 org_code 为空的记录
-- SELECT no, applicant, dept, org_code FROM polaris_contract.contract_purchase_plan
-- WHERE org_code IS NULL OR org_code = '';
