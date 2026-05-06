---
name: cancel_order
domain: food_delivery
description: 帮助用户取消已有的外卖订单
compatibility: 需要联网访问订单查询和取消API
metadata:
  author: dev-team
  version: 1.0
---

## Required Parameters
| 参数名 | 类型 | 说明 |
|--------|------|------|
| orderId | string | 要取消的订单号 |

## Optional Parameters
| 参数名 | 类型 | 说明 |
|--------|------|------|
| phone | string | 用户手机号（用于辅助查找订单） |

## Execution Flow
1. **获取订单号**: 如果用户未提供订单号，根据用户提供的线索（如菜品名、时间等）使用 orderTool 查询并确定要取消的订单，向用户确认
2. **确认取消**: 向用户展示要取消的订单详情，请用户再次确认是否取消
3. **执行取消**: 用户确认后，使用 orderTool 删除订单
4. **返回结果**: 将取消成功的订单信息展示给用户，告知已成功取消
5. **批量处理**: 如果用户要求取消所有订单，遍历查询到的所有订单逐个取消

## Related Tools
- orderTool, assignmentFinish

## Examples
- 帮我取消订单
- 我想取消今天下的那个订单
- 把订单号 20240501001 取消掉
- 我要取消所有订单
