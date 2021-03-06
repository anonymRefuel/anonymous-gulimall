package com.atguigu.gulimall.product.service.impl;

import com.atguigu.gulimall.product.dao.BrandDao;
import com.atguigu.gulimall.product.dao.CategoryDao;
import com.atguigu.gulimall.product.entity.BrandEntity;
import com.atguigu.gulimall.product.entity.CategoryEntity;
import com.atguigu.gulimall.product.service.BrandService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.product.dao.CategoryBrandRelationDao;
import com.atguigu.gulimall.product.entity.CategoryBrandRelationEntity;
import com.atguigu.gulimall.product.service.CategoryBrandRelationService;


@Service("categoryBrandRelationService")
public class CategoryBrandRelationServiceImpl extends ServiceImpl<CategoryBrandRelationDao, CategoryBrandRelationEntity> implements CategoryBrandRelationService {

    @Autowired
    BrandDao brandDao;
    @Autowired
    CategoryDao categoryDao;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<CategoryBrandRelationEntity> page = this.page(
                new Query<CategoryBrandRelationEntity>().getPage(params),
                new QueryWrapper<CategoryBrandRelationEntity>()
        );

        return new PageUtils(page);
    }

    @Override
    public List<CategoryBrandRelationEntity> listByBrandId(Long brandId) {
        LambdaQueryWrapper<CategoryBrandRelationEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        return this.list(lambdaQueryWrapper.eq(CategoryBrandRelationEntity::getBrandId, brandId));
    }

    /**
     * ?????????save?????????????????? brand_id ??? catelog_id ???????????????,???????????? brand_name ??? catelog_name
     * ?????? brand_id???catelog_id???brand_name???catelog_name
     *
     * @param categoryBrandRelation
     */
    @Override
    public void saveDetail(CategoryBrandRelationEntity categoryBrandRelation) {
        Long brandId = categoryBrandRelation.getBrandId();
        Long catelogId = categoryBrandRelation.getCatelogId();

        //??????brandId ?????? brandName
        BrandEntity brandEntity = brandDao.selectById(brandId);
        //?????? categoryId ?????? categoryName
        CategoryEntity categoryEntity = categoryDao.selectById(catelogId);

        categoryBrandRelation.setBrandName(brandEntity.getName());
        categoryBrandRelation.setCatelogName(categoryEntity.getName());

        this.save(categoryBrandRelation);
    }

    @Override
    public void updateBrand(BrandEntity brand) {
        CategoryBrandRelationEntity categoryBrandRelationEntity = new CategoryBrandRelationEntity();
        categoryBrandRelationEntity.setBrandId(brand.getBrandId());
        categoryBrandRelationEntity.setBrandName(brand.getName());

        LambdaUpdateWrapper<CategoryBrandRelationEntity> lambdaUpdateWrapper = new LambdaUpdateWrapper<>();
        lambdaUpdateWrapper.eq(CategoryBrandRelationEntity::getBrandId, brand.getBrandId());
        this.update(categoryBrandRelationEntity, lambdaUpdateWrapper);
    }

    @Override
    public void updateCategory(CategoryEntity category) {
       this.baseMapper.updateCategory(category);
    }

    @Override
    public List<BrandEntity> getBrandsByCatId(Long catId) {
        LambdaQueryWrapper<CategoryBrandRelationEntity> categoryBrandRelationQueryWrapper = new LambdaQueryWrapper<>();
        categoryBrandRelationQueryWrapper.eq(CategoryBrandRelationEntity::getCatelogId,catId);
        List<CategoryBrandRelationEntity> categoryBrandRelationEntities = this.baseMapper.selectList(categoryBrandRelationQueryWrapper);

        List<Long> brandIds = categoryBrandRelationEntities.stream().
                map(CategoryBrandRelationEntity::getBrandId).collect(Collectors.toList());
        List<BrandEntity> brandEntities = brandDao.selectBatchIds(brandIds);
        return brandEntities;
    }


}