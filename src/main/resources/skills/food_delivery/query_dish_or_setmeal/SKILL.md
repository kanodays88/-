---
name: dish_and_setmeal_query
domain: food_delivery
description: 帮助用户查询菜品或套餐信息，支持按口味、价格区间、分类筛选
compatibility: 需要访问菜品查询和套餐查询API
metadata:
author: dev-team
version: 1.0
---

## Additional Reference
- 各菜品口味参考：references/dish-flavor.md

## Parameters
| 参数名 | 类型 | 说明 |
|--------|------|------|
| taste | string | 口味偏好（麻辣、酸甜、清淡等，重要程度：低） |
| min_price | number | 最低价格（元，重要程度：低） |
| max_price | number | 最高价格（元，重要程度：低） |
| category | string | 菜品/套餐分类（重要程度：低） |

## Execution Flow
1. **解析意图**: 识别用户是查询特定菜品/套餐还是浏览某类菜品
2. **提取筛选条件**: 仅提取用户明确提到的口味、价格、分类信息
3. **调用工具查询**: 根据查询类型调用dishTool或setmealTool，无法区分则同时调用
4. **展示结果**: 整理并返回菜品/套餐的名称、价格、分类、图片（前四个必须要有）等信息，用表格形式返回
5. **后续交互**: 询问是否需要进一步筛选或查看详情

## Related Tools
- dishTool, setmealTool, assignmentFinish

## Examples
- 这里有什么吃的
- 帮我查一下麻辣口味的菜品
- 看看30元以下的单人套餐
- 有什么清淡的汤品推荐
- 查一下宫保鸡丁的价格