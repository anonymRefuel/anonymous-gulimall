package com.atguigu.gulimall.search.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.common.to.es.SkuEsModel;
import com.atguigu.gulimall.search.constant.EsConstant;
import com.atguigu.gulimall.search.service.MallSearchService;
import com.atguigu.gulimall.search.vo.SearchParam;
import com.atguigu.gulimall.search.vo.SearchResult;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author ?????????
 * @date 2022/7/24
 * @Description:
 */
@Service
public class MallSearchServiceImpl implements MallSearchService {

    @Autowired
    private RestHighLevelClient client;

    @Override
    public SearchResult search(SearchParam searchParam) {
        SearchResult result = null;

        //??????????????????
        SearchRequest searchRequest = buildSearchRequest(searchParam);
        try {
            SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
            result = buildSearchResponse(searchParam,response);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }



    /**
     * ??????????????????
     * ?????????????????????(??????????????????????????????????????????????????????)??????????????????????????????????????????
     * @return
     * @param searchParam
     */
    private SearchRequest buildSearchRequest(SearchParam searchParam) {

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        //?????????????????????
        BoolQueryBuilder boolQueryBuilder = boolSearch(searchParam);
        sourceBuilder.query(boolQueryBuilder);

        //??????
        //sort=saleCount_asc/desc
        //"sort": [{"skuPrice": {"order": "desc"}}]
        String sort = searchParam.getSort();
        if (StringUtils.hasText(sort)){
            String[] s = sort.split("_");
            if (s.length==2 && !sort.startsWith("_")){
                SortOrder sortOrder = "asc".equalsIgnoreCase(s[1]) ? SortOrder.ASC:SortOrder.DESC;
                //SortOrder sortOrder = SortOrder.fromString(s[1]);
                sourceBuilder.sort(s[0],sortOrder);
            }
        }

        //??????
        //"from": 0,"size": 5,
        Integer pageNum = searchParam.getPageNum();
        if (pageNum==null || pageNum<=0){
            pageNum = 1;
        }
        int from = (pageNum-1) * EsConstant.PRODUCT_PAGE_SIZE;
        sourceBuilder.from(from).size(EsConstant.PRODUCT_PAGE_SIZE);

        //??????
        //"highlight": {"fields": {"skuTitle": {}},"pre_tags": "<b style='color:red'>","post_tags": "</b>"},
        if (StringUtils.hasText(searchParam.getKeyword())) {
            HighlightBuilder highlightBuilder = new HighlightBuilder();
            highlightBuilder.field("skuTitle");
            highlightBuilder.preTags("<b style='color:red'>");
            highlightBuilder.postTags("</b>");
            sourceBuilder.highlighter(highlightBuilder);
        }

        //????????????

        //????????????
        //"aggs": {"brand_agg": {"terms": {"field": "brandId","size": 10},
        //                         "aggs": {"brand_name_agg": {"terms": {"field": "brandName","size": 1}},
        //                                  "brand_img_agg":{"terms": {"field": "brandImg","size": 1}}}}}
        TermsAggregationBuilder brandAgg = AggregationBuilders.terms("brand_agg");
        brandAgg.field("brandId").size(10);
        brandAgg.subAggregation(AggregationBuilders.terms("brand_name_agg").field("brandName").size(1));
        brandAgg.subAggregation(AggregationBuilders.terms("brand_img_agg").field("brandImg").size(1));
        sourceBuilder.aggregation(brandAgg);

        //????????????
        //"aggs": {"catalog_agg": {"terms": {"field": "catalogId","size": 10},
        //                          "aggs": {"catalog_name_agg": {"terms": {"field": "catalogName","size": 1}}}},}
        TermsAggregationBuilder catalogAgg = AggregationBuilders.terms("catalog_agg");
        catalogAgg.field("catalogId").size(10);
        catalogAgg.subAggregation(AggregationBuilders.terms("catalog_name_agg").field("catalogName").size(1));
        sourceBuilder.aggregation(catalogAgg);

        //????????????
        //"aggs": {"attr_agg":{"nested": {"path": "attrs"},
        //         "aggs": {"attr_id_agg":{"terms": {"field": "attrs.attrId","size": 10},
        //            "aggs": {"attr_name_agg": {"terms": {"field": "attrs.attrName","size": 1}},
        //                   "attr_value_agg":{"terms": {"field": "attrs.attrValue","size": 10}}}}}}}
        NestedAggregationBuilder attrAgg = AggregationBuilders.nested("attr_agg","attrs");
        TermsAggregationBuilder attrIdAgg = AggregationBuilders.terms("attr_id_agg");
        attrIdAgg.field("attrs.attrId").size(10);
        attrIdAgg.subAggregation(AggregationBuilders.terms("attr_name_agg").field("attrs.attrName").size(1));
        attrIdAgg.subAggregation(AggregationBuilders.terms("attr_value_agg").field("attrs.attrValue").size(10));
        attrAgg.subAggregation(attrIdAgg);
        sourceBuilder.aggregation(attrAgg);

        System.out.println(sourceBuilder.toString());
        return new SearchRequest(new String[]{EsConstant.PRODUCT_INDEX},sourceBuilder);
    }


    /**
     *"bool": {
     *       "must": [{"match": {"skuTitle": "??????"}}],
     *       "filter": [{"term": {"catalogId": "225"}},{"terms": {"brandId": ["1","2","9"]}},
     *         {"nested": {"path": "attrs","query": {"bool": {"must": [{"term": {"attrs.attrId": {"value": "1"}}},
     *           {"terms": {"attrs.attrValue": ["LIO-A00","A2100"]}}]}}}},
     *         {"term": {"hasStock": {"value": "true"}}},{"range": {"skuPrice": {"gte": 0,"lte": 6000}}}]
     * }
     * @param searchParam
     * @return
     */
    private BoolQueryBuilder boolSearch(SearchParam searchParam) {
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        if (StringUtils.hasText(searchParam.getKeyword())){
            //sku??????
            //"must": [{"match": {"skuTitle": "??????"}}],
            boolQueryBuilder.must(QueryBuilders.matchQuery("skuTitle",searchParam.getKeyword()));
        }
        //??????id
        //"filter": [{"term": {"catalogId": "225"}}]
        if (searchParam.getCatalog3Id()!=null){
            boolQueryBuilder.filter(QueryBuilders.termQuery("catalogId",searchParam.getCatalog3Id()));
        }
        //??????id
        //"filter": [{"terms": {"brandId": ["1","2","9"]}}]
        if (!CollectionUtils.isEmpty(searchParam.getBrandId())){
            boolQueryBuilder.filter(QueryBuilders.termsQuery("brandId",searchParam.getBrandId()));
        }
        //????????????????????????
        //"filter": [{"nested": {"path": "attrs","query": {"bool": {"must": [{"term": {"attrs.attrId": {"value": "1"}}},
        //   {"terms": {"attrs.attrValue": ["LIO-A00","A2100"]}}]}}}},{"nested","path":.......}]
        if (!CollectionUtils.isEmpty(searchParam.getAttrs())){
            List<String> attrs = searchParam.getAttrs();
            //attrs=1_??????:??????&attrs=2_5???:6???
            for (String attr : attrs) {
                //?????????????????????????????????attrId???????????????1??????2??????
                //nested????????????????????????????????????????????????????????????
                BoolQueryBuilder nestedBoolQuery = QueryBuilders.boolQuery();
                //attrs=1_??????:??????
                //s[0]=1   s[1]=??????:??????
                String[] s = attr.split("_");
                // "1_??????:??????" ==> [1,??????:??????]  length=2
                // "_??????:??????"  ==>  [,_??????:??????]  length=2
                if (s.length==2 && !attr.startsWith("_")){
                    String attrId = s[0];
                    //??????:??????
                    String[] attrValue = s[1].split(":");
                    //??????s[1] = :??????
                    if (StringUtils.hasText(attrValue[0])){
                        nestedBoolQuery.must(QueryBuilders.termQuery("attrs.attrId",attrId));
                        nestedBoolQuery.must(QueryBuilders.termsQuery("attrs.attrValue",attrValue));
                    }
                }
                //ScoreMode.None ???????????????
                NestedQueryBuilder nestedQueryBuilder = QueryBuilders.nestedQuery("attrs",nestedBoolQuery, ScoreMode.None);
                boolQueryBuilder.filter(nestedQueryBuilder);
            }
        }
        //???????????????
        //"filter": [{"term": {"hasStock": {"value": "true"}}}]
        boolean hasStock = searchParam.getHasStock()==null ||  searchParam.getHasStock()==1;
        boolQueryBuilder.filter(QueryBuilders.termQuery("hasStock",hasStock));
        //????????????????????????
        //"filter": [{"range": {"skuPrice": {"gte": 0,"lte": 6000}}}]
        if (StringUtils.hasText(searchParam.getSkuPrice())){
            RangeQueryBuilder skuPriceRange = QueryBuilders.rangeQuery("skuPrice");
            String skuPrice = searchParam.getSkuPrice();
            String[] s = skuPrice.split("_");
            //?????????1~500??????  skuPrice=1_500
            // "1_500" ==> [1, 500]  length=2
            if (s.length==2 && !skuPrice.startsWith("_")){
                skuPriceRange = skuPriceRange.gte(s[0]).lte(s[1]);
            }
            //???????????????500    skuPrice=_500
            // "_500"  ==>  [, 500]  length=2
            else if (s.length==2 && skuPrice.startsWith("_")){
                skuPriceRange = skuPriceRange.lte(s[1]);
            }
            //???????????????1      skuPrice=1_
            // "1_"    ==>  [1]      length=1
            else if (s.length==1 && skuPrice.endsWith("_")){
                skuPriceRange = skuPriceRange.gte(s[0]);
            }
            boolQueryBuilder.filter(skuPriceRange);
        }
        return boolQueryBuilder;
    }

    /**
     * ?????????????????????????????????????????????
     *
     * @param searchParam
     * @param response
     * @return
     */
    private SearchResult buildSearchResponse(SearchParam searchParam, SearchResponse response) {
        SearchHits searchHits = response.getHits();
        SearchResult searchResult = new SearchResult();
        //1????????????????????????????????????
        SearchHit[] hits = searchHits.getHits();
        List<SkuEsModel> skuEsModels = null;
        if (hits !=null && hits.length>0){
            skuEsModels = new ArrayList<>();
            for (SearchHit hit : hits) {
                String s = hit.getSourceAsString();
                SkuEsModel skuEsModel = JSON.parseObject(s, SkuEsModel.class);
                if (StringUtils.hasText(searchParam.getKeyword())) {
                    HighlightField skuTitle = hit.getHighlightFields().get("skuTitle");
                    if (skuTitle != null) {
                        Text[] fragments = skuTitle.getFragments();
                        if (fragments != null && fragments.length > 0) {
                            skuEsModel.setSkuTitle(fragments[0].string());
                        }
                    }
                }
                skuEsModels.add(skuEsModel);
            }
        }
        searchResult.setProducts(skuEsModels);

        Aggregations aggregations = response.getAggregations();
        ////2???????????????????????????????????????????????????
        List<SearchResult.AttrVo> attrVos = new ArrayList<>();
        ParsedNested attrAgg = aggregations.get("attr_agg");
        ParsedLongTerms attrIdAgg = attrAgg.getAggregations().get("attr_id_agg");
        for (Terms.Bucket attrBucket : attrIdAgg.getBuckets()) {
            SearchResult.AttrVo attrVo = new SearchResult.AttrVo();
            Long attrId = attrBucket.getKeyAsNumber().longValue();
            attrVo.setAttrId(attrId);
            ParsedStringTerms attrNameAgg = attrBucket.getAggregations().get("attr_name_agg");
            List<? extends Terms.Bucket> attrNameBuckets = attrNameAgg.getBuckets();
            if (!CollectionUtils.isEmpty(attrNameBuckets)){
                String attrName = attrNameBuckets.get(0).getKeyAsString();
                attrVo.setAttrName(attrName);
            }

            ParsedStringTerms attrValueAgg = attrBucket.getAggregations().get("attr_value_agg");
            List<? extends Terms.Bucket> attrValueBucket = attrValueAgg.getBuckets();
            if (!CollectionUtils.isEmpty(attrValueBucket)){
                List<String> attrValues = attrValueBucket.stream().map(MultiBucketsAggregation.Bucket::getKeyAsString).collect(Collectors.toList());
                attrVo.setAttrValue(attrValues);
            }
            attrVos.add(attrVo);
        }
        searchResult.setAttrs(attrVos);
        //3???????????????????????????????????????????????????
        List<SearchResult.BrandVo> brandVos = new ArrayList<>();
        ParsedLongTerms brandAgg = aggregations.get("brand_agg");
        List<? extends Terms.Bucket> brandBuckets = brandAgg.getBuckets();
        if (!CollectionUtils.isEmpty(brandBuckets)){
            for (Terms.Bucket brandBucket : brandBuckets) {
                SearchResult.BrandVo brandVo = new SearchResult.BrandVo();
                Long brandId = brandBucket.getKeyAsNumber().longValue();
                brandVo.setBrandId(brandId);

                ParsedStringTerms brandImgAgg = brandBucket.getAggregations().get("brand_img_agg");
                List<? extends Terms.Bucket> brandImgBuckets = brandImgAgg.getBuckets();
                if (!CollectionUtils.isEmpty(brandImgBuckets)){
                    String brandImg = brandImgBuckets.get(0).getKeyAsString();
                    brandVo.setBrandImg(brandImg);
                }

                ParsedStringTerms brandNameAgg = brandBucket.getAggregations().get("brand_name_agg");
                List<? extends Terms.Bucket> brandNameBuckets = brandNameAgg.getBuckets();
                if (!CollectionUtils.isEmpty(brandNameBuckets)){
                    String brandName = brandNameBuckets.get(0).getKeyAsString();
                    brandVo.setBrandName(brandName);
                }
                brandVos.add(brandVo);
            }
        }
        searchResult.setBrands(brandVos);
        //4???????????????????????????????????????????????????
        ParsedLongTerms catalogAgg = aggregations.get("catalog_agg");
        List<? extends Terms.Bucket> catalogBuckets = catalogAgg.getBuckets();
        List<SearchResult.CatalogVo> catalogVos = new ArrayList<>();
        if (!CollectionUtils.isEmpty(catalogBuckets)){
            for (Terms.Bucket catalogBucket : catalogBuckets) {
                SearchResult.CatalogVo catalogVo = new SearchResult.CatalogVo();
                long catalogId = catalogBucket.getKeyAsNumber().longValue();
                catalogVo.setCatalogId(catalogId);
                ParsedStringTerms catalogNameAgg = catalogBucket.getAggregations().get("catalog_name_agg");
                List<? extends Terms.Bucket> catalogNameBucket = catalogNameAgg.getBuckets();
                if (!CollectionUtils.isEmpty(catalogNameBucket)){
                    String catalogName = catalogNameBucket.get(0).getKeyAsString();
                    catalogVo.setCatalogName(catalogName);
                }
                catalogVos.add(catalogVo);
            }
        }
        searchResult.setCatalogs(catalogVos);
        //5???????????????-??????
        Integer pageNum = searchParam.getPageNum();
        if (pageNum ==null|| pageNum<=0){
            pageNum = 1;
        }
        searchResult.setPageNum(pageNum);
        //6???????????????-????????????
        long total = searchHits.getTotalHits().value;
        searchResult.setTotal(total);
        //7???????????????-?????????
        long totalPage = (long) Math.ceil((total/(double)EsConstant.PRODUCT_PAGE_SIZE));
        //long totalPage = (total-1)%EsConstant.PRODUCT_PAGE_SIZE +1;
        if (totalPage> Integer.MAX_VALUE){
            totalPage = Integer.MAX_VALUE;
        }
        searchResult.setTotalPages((int)totalPage);

        return searchResult;
    }


}
