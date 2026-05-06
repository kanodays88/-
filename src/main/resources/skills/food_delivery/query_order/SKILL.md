---
name: query_order
domain: food_delivery
description: 帮助用户查询已有的外卖订单信息
compatibility: 需要联网访问订单查询API
metadata:
  author: dev-team
  version: 1.0
---

## Required Parameters
| 参数名 | 类型 | 说明 |
|--------|------|------|
| phone | string | 用户下单时使用的电话号码 |

## Optional Parameters
| 参数名 | 类型 | 说明 |
|--------|------|------|
| orderId | string | 具体要查询的订单号（如果不提供则查询该手机号下所有订单） |

## Execution Flow
1. **获取用户手机号**: 如果用户未提供手机号，主动询问用户下单时使用的电话号码
2. **查询订单**: 使用 orderTool 根据手机号（和可选的订单号）查询订单信息
3. **展示结果**: 将订单信息以表格形式展示给用户，包含订单号、菜品、金额、状态、下单时间等
4. **后续处理**: 询问用户是否需要取消订单或有其他需求

## Related Tools
- orderTool, assignmentFinish

## Examples
- 查一下我的订单
- 我想查看订单状态，手机号是13800138000
- 帮我查一下订单号 20240501001 的进度
