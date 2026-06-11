import json, os

DATA_VIEW_ID = "card-analytics-dataview"

def ref(name):
    return {"name": name, "type": "index-pattern", "id": DATA_VIEW_ID}

def search_src(query=""):
    return json.dumps({
        "query": {"language": "kuery", "query": query},
        "filter": [],
        "indexRefName": "kibanaSavedObjectMeta.searchSourceJSON.index"
    })

def make_viz(vid, title, vis_type, aggs, params, query=""):
    vis_state = json.dumps({"title": title, "type": vis_type, "aggs": aggs, "params": params})
    return {
        "type": "visualization", "id": vid,
        "attributes": {
            "title": title,
            "visState": vis_state,
            "uiStateJSON": "{}",
            "description": "",
            "kibanaSavedObjectMeta": {"searchSourceJSON": search_src(query)}
        },
        "references": [ref("kibanaSavedObjectMeta.searchSourceJSON.index")],
        "coreMigrationVersion": "8.8.0",
        "typeMigrationVersion": "8.5.0",
        "managed": False
    }

# ── 공통 파이차트 params ──────────────────────────────────────────────────────
PIE_PARAMS = {
    "type": "pie",
    "addTooltip": True,
    "addLegend": True,
    "legendPosition": "right",
    "isDonut": True,
    "labels": {"show": False, "values": True, "last_level": True, "truncate": 100}
}

# ── 공통 메트릭 params ────────────────────────────────────────────────────────
def metric_params(font_size=60, sub_text=""):
    return {
        "metric": {
            "percentageMode": False,
            "useRanges": False,
            "colorSchema": "Green to Red",
            "metricColorMode": "None",
            "colorsRange": [{"type": "range", "from": 0, "to": 10000}],
            "labels": {"show": True},
            "invertColors": False,
            "style": {
                "bgFill": "#000", "bgColor": False, "labelColor": False,
                "subText": sub_text, "fontSize": font_size
            }
        },
        "dimensions": {
            "metrics": [{"accessor": 0, "format": {"id": "number", "params": {}},
                         "params": {}, "aggType": "count"}],
            "buckets": []
        }
    }

COUNT_AGG = [{"id": "1", "enabled": True, "type": "count", "schema": "metric", "params": {}}]

def terms_agg(field, size=10, other=True):
    return {"id": "2", "enabled": True, "type": "terms", "schema": "segment",
            "params": {"field": field, "orderBy": "1", "order": "desc", "size": size,
                       "otherBucket": other, "otherBucketLabel": "기타", "missingBucket": False}}

def table_terms_agg(field, size=10):
    return {"id": "2", "enabled": True, "type": "terms", "schema": "bucket",
            "params": {"field": field, "orderBy": "1", "order": "desc", "size": size,
                       "otherBucket": False, "missingBucket": False}}

# ── Object 1: Index Pattern ───────────────────────────────────────────────────
obj_index = {
    "type": "index-pattern", "id": DATA_VIEW_ID,
    "attributes": {
        "allowNoIndex": False, "fieldAttrs": "{}", "fieldFormatMap": "{}",
        "fields": "[]", "name": "Card Analytics", "runtimeFieldMap": "{}",
        "timeFieldName": "@timestamp", "title": "card-analytics-*"
    },
    "references": [], "coreMigrationVersion": "8.8.0",
    "typeMigrationVersion": "8.0.0", "managed": False
}

# ── Viz 1: 총 카드 조회수 (metric) ────────────────────────────────────────────
viz_view_count = make_viz(
    "viz-view-count", "총 카드 조회수", "metric",
    COUNT_AGG,
    metric_params(font_size=60, sub_text="Card Views"),
    query="event_type: card_view"
)

# ── Viz 2: 가장 많이 조회된 카드 Top 10 (donut pie) ───────────────────────────
viz_card_ranking = make_viz(
    "viz-card-ranking", "가장 많이 조회된 카드 Top 10", "pie",
    COUNT_AGG + [terms_agg("card_name.keyword", size=10)],
    PIE_PARAMS,
    query="event_type: card_view"
)

# ── Viz 3: 인기 검색 키워드 Top 10 (donut pie) ────────────────────────────────
viz_keyword_ranking = make_viz(
    "viz-keyword-ranking", "인기 검색 키워드 Top 10", "pie",
    COUNT_AGG + [terms_agg("search_keyword.keyword", size=10, other=False)],
    PIE_PARAMS,
    query="event_type: card_search"
)

# ── Viz 4: 총 검색 횟수 (metric) ─────────────────────────────────────────────
viz_search_count = make_viz(
    "viz-search-count", "총 검색 횟수", "metric",
    COUNT_AGG,
    metric_params(font_size=60, sub_text="Card Searches"),
    query="event_type: card_search"
)

# ── Viz 5: 시간대별 API 트래픽 (stacked bar) ──────────────────────────────────
viz_traffic = make_viz(
    "viz-traffic", "시간대별 API 트래픽", "histogram",
    [
        {"id": "1", "enabled": True, "type": "count", "schema": "metric", "params": {}},
        {"id": "2", "enabled": True, "type": "date_histogram", "schema": "segment",
         "params": {"field": "@timestamp", "useNormalizedEsInterval": True,
                    "scaleMetricValues": False, "interval": "auto",
                    "drop_partials": False, "min_doc_count": 1, "extended_bounds": {}}},
        {"id": "3", "enabled": True, "type": "terms", "schema": "group",
         "params": {"field": "event_type.keyword", "orderBy": "1", "order": "desc",
                    "size": 5, "otherBucket": False, "missingBucket": False}}
    ],
    {
        "type": "histogram",
        "grid": {"categoryLines": False},
        "categoryAxes": [{"id": "CategoryAxis-1", "type": "category", "position": "bottom",
                          "show": True, "style": {}, "scale": {"type": "linear"},
                          "labels": {"show": True, "filter": True, "truncate": 100}, "title": {}}],
        "valueAxes": [{"id": "ValueAxis-1", "name": "LeftAxis-1", "type": "value",
                       "position": "left", "show": True, "style": {},
                       "scale": {"type": "linear", "mode": "stacked"},
                       "labels": {"show": True, "rotate": 0, "filter": False, "truncate": 100},
                       "title": {"text": "요청 수"}}],
        "seriesParams": [{"show": True, "type": "histogram", "mode": "stacked",
                          "data": {"label": "Count", "id": "1"}, "valueAxis": "ValueAxis-1",
                          "drawLinesBetweenPoints": True}],
        "addTooltip": True, "addLegend": True, "legendPosition": "right",
        "times": [], "addTimeMarker": False,
        "thresholdLine": {"show": False, "value": 10, "width": 1, "style": "full", "color": "#34130C"},
        "labels": {"show": False}
    }
)

# ── Viz 6: 유저별 활동 Top 10 (donut pie) ─────────────────────────────────────
viz_user_activity = make_viz(
    "viz-user-activity", "유저별 활동 Top 10", "pie",
    COUNT_AGG + [{"id": "2", "enabled": True, "type": "terms", "schema": "segment",
                  "params": {"field": "user_id.keyword", "orderBy": "1", "order": "desc",
                             "size": 10, "otherBucket": False,
                             "missingBucket": True, "missingBucketLabel": "anonymous"}}],
    PIE_PARAMS
)

# ── Viz 9: 경매별 입찰 Top 10 (horizontal bar) ────────────────────────────────
viz_auction_bid_ranking = make_viz(
    "viz-auction-bid-ranking", "경매별 입찰 Top 10", "histogram",
    [
        {"id": "1", "enabled": True, "type": "count", "schema": "metric", "params": {}},
        {"id": "2", "enabled": True, "type": "terms", "schema": "segment",
         "params": {"field": "auction_id", "orderBy": "1", "order": "desc",
                    "size": 10, "otherBucket": False, "missingBucket": False}}
    ],
    {
        "type": "histogram",
        "grid": {"categoryLines": False},
        "categoryAxes": [{"id": "CategoryAxis-1", "type": "category", "position": "bottom",
                          "show": True, "style": {}, "scale": {"type": "linear"},
                          "labels": {"show": True, "filter": True, "truncate": 100}, "title": {}}],
        "valueAxes": [{"id": "ValueAxis-1", "name": "LeftAxis-1", "type": "value",
                       "position": "left", "show": True, "style": {},
                       "scale": {"type": "linear", "mode": "normal"},
                       "labels": {"show": True, "rotate": 0, "filter": False, "truncate": 100},
                       "title": {"text": "입찰 수"}}],
        "seriesParams": [{"show": True, "type": "histogram", "mode": "normal",
                          "data": {"label": "Count", "id": "1"}, "valueAxis": "ValueAxis-1",
                          "drawLinesBetweenPoints": True}],
        "addTooltip": True, "addLegend": True, "legendPosition": "right",
        "times": [], "addTimeMarker": False,
        "thresholdLine": {"show": False, "value": 10, "width": 1, "style": "full", "color": "#34130C"},
        "labels": {"show": False}
    },
    query="event_type: auction_bid"
)

# ── Viz 10: 거래 많이된 카드 Top 10 (donut pie) ───────────────────────────────
viz_card_trade_ranking = make_viz(
    "viz-card-trade-ranking", "거래 많이된 카드 Top 10", "pie",
    COUNT_AGG + [terms_agg("card_name.keyword", size=10, other=False)],
    PIE_PARAMS,
    query="event_type: card_trade"
)

# ── Viz 11: 총 거래 완료 수 (metric) ──────────────────────────────────────────
viz_trade_count = make_viz(
    "viz-trade-count", "총 거래 완료 수", "metric",
    COUNT_AGG,
    metric_params(font_size=60, sub_text="Completed Trades"),
    query="event_type: card_trade"
)

# ── Viz 7: 카드 조회 상세 테이블 ──────────────────────────────────────────────
viz_view_table = make_viz(
    "viz-view-table", "카드 조회 상세", "table",
    [
        {"id": "1", "enabled": True, "type": "count", "schema": "metric", "params": {}},
        table_terms_agg("card_name.keyword", size=10)
    ],
    {
        "perPage": 10,
        "showPartialRows": False,
        "showMetricsAtAllLevels": False,
        "sort": {"columnIndex": None, "direction": None},
        "showTotal": False,
        "totalFunc": "sum",
        "dimensions": {
            "metrics": [{"accessor": 1, "format": {"id": "number", "params": {}},
                         "params": {}, "aggType": "count"}],
            "buckets": [{"accessor": 0, "format": {"id": "terms",
                         "params": {"id": "string", "missingBucketLabel": "Missing",
                                    "otherBucketLabel": "기타"}},
                         "params": {}, "aggType": "terms"}]
        }
    },
    query="event_type: card_view"
)

# ── Viz 8: 검색 키워드 상세 테이블 ────────────────────────────────────────────
viz_search_table = make_viz(
    "viz-search-table", "검색 키워드 상세", "table",
    [
        {"id": "1", "enabled": True, "type": "count", "schema": "metric", "params": {}},
        table_terms_agg("search_keyword.keyword", size=10)
    ],
    {
        "perPage": 10,
        "showPartialRows": False,
        "showMetricsAtAllLevels": False,
        "sort": {"columnIndex": None, "direction": None},
        "showTotal": False,
        "totalFunc": "sum",
        "dimensions": {
            "metrics": [{"accessor": 1, "format": {"id": "number", "params": {}},
                         "params": {}, "aggType": "count"}],
            "buckets": [{"accessor": 0, "format": {"id": "terms",
                         "params": {"id": "string", "missingBucketLabel": "Missing",
                                    "otherBucketLabel": "기타"}},
                         "params": {}, "aggType": "terms"}]
        }
    },
    query="event_type: card_search"
)

# ── Dashboard ─────────────────────────────────────────────────────────────────
# 레이아웃 (총 너비 48):
# Row1 y=0  h=10: metric view(w=8) | pie card(w=16) | pie keyword(w=16) | metric search(w=8)
# Row2 y=10 h=18: stacked bar(w=32) | pie user activity(w=16)
# Row3 y=28 h=14: table view(w=24) | table search(w=24)
# Row4 y=42 h=14: auction bid ranking(w=48)
# Row5 y=56 h=14: metric trade(w=8) | pie trade ranking(w=40)
panels = [
    {"version":"8.18.0","type":"visualization","gridData":{"x": 0,"y": 0,"w": 8,"h":10,"i":"1"},"panelIndex":"1","embeddableConfig":{"enhancements":{}},"panelRefName":"panel_1"},
    {"version":"8.18.0","type":"visualization","gridData":{"x": 8,"y": 0,"w":16,"h":10,"i":"2"},"panelIndex":"2","embeddableConfig":{"enhancements":{}},"panelRefName":"panel_2"},
    {"version":"8.18.0","type":"visualization","gridData":{"x":24,"y": 0,"w":16,"h":10,"i":"3"},"panelIndex":"3","embeddableConfig":{"enhancements":{}},"panelRefName":"panel_3"},
    {"version":"8.18.0","type":"visualization","gridData":{"x":40,"y": 0,"w": 8,"h":10,"i":"4"},"panelIndex":"4","embeddableConfig":{"enhancements":{}},"panelRefName":"panel_4"},
    {"version":"8.18.0","type":"visualization","gridData":{"x": 0,"y":10,"w":32,"h":18,"i":"5"},"panelIndex":"5","embeddableConfig":{"enhancements":{}},"panelRefName":"panel_5"},
    {"version":"8.18.0","type":"visualization","gridData":{"x":32,"y":10,"w":16,"h":18,"i":"6"},"panelIndex":"6","embeddableConfig":{"enhancements":{}},"panelRefName":"panel_6"},
    {"version":"8.18.0","type":"visualization","gridData":{"x": 0,"y":28,"w":24,"h":14,"i":"7"},"panelIndex":"7","embeddableConfig":{"enhancements":{}},"panelRefName":"panel_7"},
    {"version":"8.18.0","type":"visualization","gridData":{"x":24,"y":28,"w":24,"h":14,"i":"8"},"panelIndex":"8","embeddableConfig":{"enhancements":{}},"panelRefName":"panel_8"},
    {"version":"8.18.0","type":"visualization","gridData":{"x": 0,"y":42,"w":48,"h":14,"i":"9"},"panelIndex":"9","embeddableConfig":{"enhancements":{}},"panelRefName":"panel_9"},
    {"version":"8.18.0","type":"visualization","gridData":{"x": 0,"y":56,"w": 8,"h":14,"i":"10"},"panelIndex":"10","embeddableConfig":{"enhancements":{}},"panelRefName":"panel_10"},
    {"version":"8.18.0","type":"visualization","gridData":{"x": 8,"y":56,"w":40,"h":14,"i":"11"},"panelIndex":"11","embeddableConfig":{"enhancements":{}},"panelRefName":"panel_11"},
]

obj_dashboard = {
    "type": "dashboard", "id": "card-analytics-dashboard",
    "attributes": {
        "title": "카드 Analytics 대시보드",
        "hits": 0,
        "description": "카드 조회 랭킹 | 검색 키워드 | 시간대별 트래픽 | 유저별 활동",
        "panelsJSON": json.dumps(panels),
        "optionsJSON": json.dumps({"useMargins": True, "syncColors": False, "hidePanelTitles": False}),
        "version": 2,
        "timeRestore": False,
        "kibanaSavedObjectMeta": {
            "searchSourceJSON": json.dumps({"query": {"language": "kuery", "query": ""}, "filter": []})
        }
    },
    "references": [
        {"name": "panel_1", "type": "visualization", "id": "viz-view-count"},
        {"name": "panel_2", "type": "visualization", "id": "viz-card-ranking"},
        {"name": "panel_3", "type": "visualization", "id": "viz-keyword-ranking"},
        {"name": "panel_4", "type": "visualization", "id": "viz-search-count"},
        {"name": "panel_5", "type": "visualization", "id": "viz-traffic"},
        {"name": "panel_6", "type": "visualization", "id": "viz-user-activity"},
        {"name": "panel_7", "type": "visualization", "id": "viz-view-table"},
        {"name": "panel_8", "type": "visualization", "id": "viz-search-table"},
        {"name": "panel_9",  "type": "visualization", "id": "viz-auction-bid-ranking"},
        {"name": "panel_10", "type": "visualization", "id": "viz-trade-count"},
        {"name": "panel_11", "type": "visualization", "id": "viz-card-trade-ranking"},
    ],
    "coreMigrationVersion": "8.8.0",
    "typeMigrationVersion": "8.7.0",
    "managed": False
}

# ── Write NDJSON ──────────────────────────────────────────────────────────────
all_objects = [
    obj_index,
    viz_view_count, viz_card_ranking, viz_keyword_ranking, viz_search_count,
    viz_traffic, viz_user_activity,
    viz_view_table, viz_search_table,
    viz_auction_bid_ranking,
    viz_card_trade_ranking, viz_trade_count,
    obj_dashboard
]

out_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "card-analytics-dashboard.ndjson")
with open(out_path, "w", encoding="utf-8") as f:
    for obj in all_objects:
        f.write(json.dumps(obj, ensure_ascii=False) + "\n")

print("Written:", out_path)
with open(out_path, encoding="utf-8") as f:
    for i, line in enumerate(f, 1):
        json.loads(line)
        print(f"  line {i:2d}: valid JSON OK")
