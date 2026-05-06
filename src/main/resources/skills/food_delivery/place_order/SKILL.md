---
name: place_order
domain: food_delivery
description: 帮助用户完成外卖下单，包括查询菜品/套餐、收集配送信息、确认并生成订单
compatibility: 需要联网访问菜品查询和订单生成API
metadata:
  author: dev-team
  version: 1.0
---

## Required Parameters
| 参数名 | 类型 | 说明 |
|--------|------|------|
| items | string[] | 用户想要购买的菜品或套餐名称列表 |
| address | string | 配送地址 |
| phone | string | 用户电话号码 |

## Optional Parameters
| 参数名 | 类型 | 说明 |
|--------|------|------|
| note | string | 订单备注信息 |

## Execution Flow
1. **查询菜品或套餐详情**: 使用 dishTool 或 setmealTool 查询用户提到的菜品/套餐的具体信息（名称、价格、图片等）
2. **收集配送信息**: 如果用户未提供配送地址或电话，主动询问并收集用户的地址、电话号码、备注（可选）
3. **确认订单信息**: 将查询到的菜品/套餐信息连同配送地址、电话、备注整理成订单详情，向用户展示并请用户确认
4. **生成订单**: 用户确认后，使用 orderTool 生成订单，传入菜品/套餐信息、地址、电话、备注
5. **返回结果**: 将生成的订单信息（订单号、菜品、金额、配送地址等）展示给用户，并询问是否还有其他需要

## Related Tools
- dishTool, setmealTool, orderTool, assignmentFinish

## Examples
- 我要一份鱼香肉丝饭，送到科技园A座，电话13800138000
- 帮我下单：2份黄焖鸡米饭，地址是人民路15号，手机号139xxxx
- 我想点餐，有什么推荐的吗
