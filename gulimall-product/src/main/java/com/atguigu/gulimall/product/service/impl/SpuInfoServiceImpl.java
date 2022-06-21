package com.atguigu.gulimall.product.service.impl;

import com.atguigu.common.to.SkuReductionTo;
import com.atguigu.common.to.SpuBoundTo;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.product.dao.SpuInfoDao;
import com.atguigu.gulimall.product.entity.*;
import com.atguigu.gulimall.product.feign.CouponFeignService;
import com.atguigu.gulimall.product.service.*;
import com.atguigu.gulimall.product.vo.SpuSaveVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


@Service("spuInfoService")
public class SpuInfoServiceImpl extends ServiceImpl<SpuInfoDao, SpuInfoEntity> implements SpuInfoService {

    @Autowired
    SpuInfoDescService spuInfoDescService;
    @Autowired
    SpuImagesService spuImagesService;
    @Autowired
    AttrService attrService;
    @Autowired
    ProductAttrValueService productAttrValueService;
    @Autowired
    SkuInfoService skuInfoService;
    @Autowired
    SkuImagesService skuImagesService;
    @Autowired
    SkuSaleAttrValueService skuSaleAttrValueService;
    @Autowired
    CouponFeignService couponFeignService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<SpuInfoEntity> page = this.page(
                new Query<SpuInfoEntity>().getPage(params),
                new QueryWrapper<SpuInfoEntity>()
        );

        return new PageUtils(page);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void saveSpuInfo(SpuSaveVo spuSaveVo) {
        //1、保存spu基本信息 pms_spu_info
        SpuInfoEntity spuInfoEntity = new SpuInfoEntity();
        BeanUtils.copyProperties(spuSaveVo,spuInfoEntity);
        this.saveBaseSpuInfo(spuInfoEntity);

        //2、保存Spu的描述 pms_spu_info_desc
        List<String> decript = spuSaveVo.getDecript();
        if (decript!=null && decript.size()>0) {
            SpuInfoDescEntity spuInfoDescEntity = new SpuInfoDescEntity();
            spuInfoDescEntity.setSpuId(spuInfoEntity.getId());
            spuInfoDescEntity.setDecript(String.join(",", decript));
            spuInfoDescService.saveSpuInfoDesc(spuInfoDescEntity);
        }

        //3、保存spu的图片集 pms_spu_images
        List<String> images = spuSaveVo.getImages();
        if (images!=null && images.size()>0) {
            spuImagesService.saveImages(spuInfoEntity.getId(), images);
        }

        //4、保存spu的规格参数；pms_product_attr_value
        List<SpuSaveVo.BaseAttrs> baseAttrs = spuSaveVo.getBaseAttrs();
        if (!CollectionUtils.isEmpty(baseAttrs)) {
            List<ProductAttrValueEntity> productAttrValueEntities = baseAttrs.stream().map(attr -> {
                ProductAttrValueEntity productAttrValueEntity = new ProductAttrValueEntity();
                productAttrValueEntity.setSpuId(spuInfoEntity.getId());
                if (attr.getAttrId() != null) {
                    productAttrValueEntity.setAttrId(attr.getAttrId());
                    productAttrValueEntity.setAttrValue(attr.getAttrValues());
                    productAttrValueEntity.setQuickShow(attr.getShowDesc());
                    AttrEntity attrEntity = attrService.getById(attr.getAttrId());
                    if (attrEntity != null) {
                        productAttrValueEntity.setAttrName(attrEntity.getAttrName());
                    }
                }
                return productAttrValueEntity;
            }).collect(Collectors.toList());

            productAttrValueService.saveProductAttr(productAttrValueEntities);
        }

        //5、保存spu的积分信息; gulimall_sms->sms_spu_bounds
        SpuSaveVo.Bounds bounds = spuSaveVo.getBounds();
        SpuBoundTo spuBoundTo = new SpuBoundTo();
        BeanUtils.copyProperties(bounds,spuBoundTo);
        spuBoundTo.setSpuId(spuInfoEntity.getId());
        R r = couponFeignService.saveSpuBounds(spuBoundTo);
        if (r.getCode()!=0){
            log.error("远程保存spu积分信息失败");
        }

        //5、保存当前spu对应的所有sku信息;
        List<SpuSaveVo.Skus> skus = spuSaveVo.getSkus();
        if (!CollectionUtils.isEmpty(skus)){
            //由于spu的id需要与 图片 销售属性 等进行关联，所以不能调用批量保存方法
            skus.forEach(sku->{
                //5.1)、sku的基本信息; pms_sku_info
                SkuInfoEntity skuInfoEntity = new SkuInfoEntity();
                //private String skuName;
                //private BigDecimal price;
                //private String skuTitle;
                //private String skuSubtitle;
                BeanUtils.copyProperties(sku,skuInfoEntity);
                //private Long spuId;
                skuInfoEntity.setSpuId(spuInfoEntity.getId());
                //private String skuDesc;
                //private Long catalogId;
                skuInfoEntity.setCatalogId(spuInfoEntity.getCatalogId());
                //private Long brandId;
                skuInfoEntity.setBrandId(spuInfoEntity.getBrandId());
                //private String skuDefaultImg;
                List<SpuSaveVo.Images> skuImages = sku.getImages();
                Optional<SpuSaveVo.Images> defaultImgOptional = skuImages.stream().filter(item -> item.getDefaultImg() == 1).findFirst();
                defaultImgOptional.ifPresent(defaultImg -> skuInfoEntity.setSkuDefaultImg(defaultImg.getImgUrl()));
                //private Long saleCount;
                skuInfoEntity.setSaleCount(0L);
                skuInfoService.saveSkuInfo(skuInfoEntity);
                //保存sku基本信息后，会返回新增数据生成的id
                Long skuId = skuInfoEntity.getSkuId();

                //5.2)、sku的图片信息; pms_sku_images
                List<SkuImagesEntity> skuImagesEntities = sku.getImages().stream().map(img -> {
                    SkuImagesEntity skuImagesEntity = new SkuImagesEntity();
                    skuImagesEntity.setSkuId(skuId);
                    skuImagesEntity.setImgUrl(img.getImgUrl());
                    skuImagesEntity.setDefaultImg(img.getDefaultImg());
                    return skuImagesEntity;
                }).filter(entry->{
                    //如果图片的url为空，就过滤掉
                    return StringUtils.hasLength(entry.getImgUrl());
                }).collect(Collectors.toList());
                skuImagesService.saveBatch(skuImagesEntities);
                //TODO 没有图片；路径的无需保存
                //5.3)、sku的销售属性信息: pms_sku_sale_attr_value
                List<SpuSaveVo.Attr> attrs = sku.getAttr();
                List<SkuSaleAttrValueEntity> skuSaleAttrValueEntities = attrs.stream().map(attr -> {
                    SkuSaleAttrValueEntity skuSaleAttrValueEntity = new SkuSaleAttrValueEntity();
                    BeanUtils.copyProperties(attr, skuSaleAttrValueEntity);
                    skuSaleAttrValueEntity.setSkuId(skuId);
                    return skuSaleAttrValueEntity;
                }).collect(Collectors.toList());
                skuSaleAttrValueService.saveBatch(skuSaleAttrValueEntities);

                //5.4)、sku的优惠、满减、打折等信息；gulimall_sms->sms_sku_ladder\sms_sku_full_reduction\sms_ member_price
                SkuReductionTo skuReductionTo = new SkuReductionTo();
                BeanUtils.copyProperties(sku,skuReductionTo);
                skuReductionTo.setSkuId(skuId);
                System.out.println(skuReductionTo.getMemberPrice());

                Optional<SpuSaveVo.MemberPrice> firstMemberPrice = sku.getMemberPrice().stream()
                        .filter(memberPrice -> memberPrice.getPrice().compareTo(BigDecimal.ZERO) > 0)
                        .findFirst();

                //满几件打几折、满多少减多少、会员价格，如果有一项有数据才调用远程服务
                if (skuReductionTo.getFullCount()>0
                        || skuReductionTo.getFullPrice().compareTo(BigDecimal.ZERO) > 0
                        || firstMemberPrice.isPresent()){
                    R r1 = couponFeignService.saveSkuReduction(skuReductionTo);
                    if (r1.getCode()!=0){
                        log.error("远程保存sku优惠信息失败");
                    }
                }
            });
        }


    }

    @Override
    public void saveBaseSpuInfo(SpuInfoEntity spuInfoEntity) {
        this.baseMapper.insert(spuInfoEntity);
    }

    /**
     * 根据条件分页查询
     * {
     *    page: 1,//当前页码
     *    limit: 10,//每页记录数
     *    sidx: 'id',//排序字段
     *    order: 'asc/desc',//排序方式
     *    key: '华为',//检索关键字
     *    catelogId: 6,//三级分类id
     *    brandId: 1,//品牌id
     *    status: 0,//商品状态
     * }
     * @param params
     * @return
     */
    @Override
    public PageUtils queryPageByCondition(Map<String, Object> params) {
        LambdaQueryWrapper<SpuInfoEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        //根据"key"，精确匹配商品id 或 模糊查询spu_name
        String key = (String) params.get("key");
        lambdaQueryWrapper.and(StringUtils.hasLength(key) && !"0".equalsIgnoreCase(key),wrapper->{
            wrapper.eq(SpuInfoEntity::getId,key).or().like(SpuInfoEntity::getSpuName,key);
        });
        //根据status精确匹配状态
        String status = (String) params.get("status");
        lambdaQueryWrapper.eq(StringUtils.hasLength(status) && !"0".equalsIgnoreCase(status),SpuInfoEntity::getPublishStatus,status);
        //根据brandId精确匹配品牌id
        String brandId = (String) params.get("brandId");
        lambdaQueryWrapper.eq(StringUtils.hasLength(brandId) && !"0".equalsIgnoreCase(brandId),SpuInfoEntity::getBrandId,brandId);
        //根据catelogId精确匹配所属分类id（注意：前端发来的是catelogId,数据库写的是catalogId）
        String catelogId = (String) params.get("catelogId");
        lambdaQueryWrapper.eq(StringUtils.hasLength(catelogId) && !"0".equalsIgnoreCase(catelogId),SpuInfoEntity::getCatalogId,catelogId);

        IPage<SpuInfoEntity> page = this.page(
                new Query<SpuInfoEntity>().getPage(params),
                lambdaQueryWrapper
        );

        return new PageUtils(page);
    }



}