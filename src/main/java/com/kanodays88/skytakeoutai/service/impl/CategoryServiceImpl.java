package com.kanodays88.skytakeoutai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kanodays88.skytakeoutai.entity.Category;
import com.kanodays88.skytakeoutai.service.CategoryService;
import com.kanodays88.skytakeoutai.mapper.CategoryMapper;
import org.springframework.stereotype.Service;

/**
* @author Administrator
* @description 针对表【category(菜品及套餐分类)】的数据库操作Service实现
* @createDate 2026-04-03 21:42:53
*/
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category>
    implements CategoryService{

}




