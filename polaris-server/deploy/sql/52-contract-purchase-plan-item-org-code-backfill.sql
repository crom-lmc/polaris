-- 采购计划明细归属组织回填（幂等）
-- 背景：历史明细 org_code 为空；详情 itemsOf 曾对明细单独套 applyOrgScope，空串被过滤，
--       导致详情「采购明细」为空、明细数 0、计划金额 0。
-- 口径：父子一体，明细 org_code 跟随主表；只填空/NULL，不改已有归属。
-- 代码侧已移除 itemsOf 的重复数据权限过滤，本脚本仅用于修数据一致性。

UPDATE polaris_contract.contract_purchase_plan_item i
JOIN polaris_contract.contract_purchase_plan p ON p.no = i.plan_no
SET i.org_code = p.org_code,
    i.updated_at = NOW()
WHERE (i.org_code IS NULL OR i.org_code = '')
  AND p.org_code IS NOT NULL
  AND p.org_code <> ''
  AND i.deleted = 0
  AND p.deleted = 0;

-- 核对：应无 org_code 为空的明细
-- SELECT i.plan_no, i.material_name, i.org_code FROM polaris_contract.contract_purchase_plan_item i
-- WHERE i.org_code IS NULL OR i.org_code = '';
