package com.app.ev119.service;

import com.app.ev119.domain.dto.request.CheckEmergencyRealtimeRequestDTO;
import com.app.ev119.domain.dto.request.SearchEmergencyLocationInfoRequestDTO;
import com.app.ev119.domain.dto.response.CheckEmergencyRealtimeItem;
import com.app.ev119.domain.dto.response.CheckEmergencyRealtimeResponse;
import com.app.ev119.domain.dto.response.SearchEmergencyLocationInfoItem;
import com.app.ev119.domain.dto.response.SearchEmergencyLocationInfoResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchEmergencyLocationInfoService {

    private final RestTemplate restTemplate;
    private final CheckEmergencyRealtimeService checkEmergencyRealtimeService;

    @Value("${api.emergency.service-key}")
    private String serviceKey;

    @Value("${api.emergency.search-emergency-location-url}")
    private String searchEmergencyLocationUrl;

    
    public SearchEmergencyLocationInfoResponse getSearchEmergencyLocationInfo(SearchEmergencyLocationInfoRequestDTO req) {
        String url = UriComponentsBuilder
                .fromHttpUrl(searchEmergencyLocationUrl)
                .queryParam("serviceKey", serviceKey)
                .queryParam("WGS84_LON", req.getWgs84Lon())
                .queryParam("WGS84_LAT", req.getWgs84Lat())
                .queryParam("pageNo", req.getPageNo())
                .queryParam("numOfRows", req.getNumOfRows())
                .toUriString();

        return restTemplate.getForObject(url, SearchEmergencyLocationInfoResponse.class);
    }


    public SearchEmergencyLocationInfoResponse getSearchEmergencyLocationInfoWithStatus(SearchEmergencyLocationInfoRequestDTO req) {

        SearchEmergencyLocationInfoResponse base = getSearchEmergencyLocationInfo(req);
        if (base == null || base.getBody() == null || base.getBody().getItems() == null || base.getBody().getItems().isEmpty()) {
            return base;
        }

        List<SearchEmergencyLocationInfoItem> items = base.getBody().getItems();


        LinkedHashSet<String> stagePairs = new LinkedHashSet<>();
        for (SearchEmergencyLocationInfoItem it : items) {
            Stage st = extractStage1Stage2(it.getDutyAddr());
            if (st.stage1 != null && st.stage2 != null) {
                stagePairs.add(st.stage1 + "||" + st.stage2);
            }
        }

        if (stagePairs.isEmpty()) return base;


        Map<String, CheckEmergencyRealtimeItem> rtMap = new HashMap<>();

        for (String pair : stagePairs) {
            String[] p = pair.split("\\|\\|");
            if (p.length < 2) continue;

            String stage1 = normalizeStage1(p[0]);
            String stage2 = normalizeStage2(p[1]);

            if (stage1 == null || stage2 == null) continue;

            mergeRealtimeWithFallback(stage1, stage2, rtMap);
        }


        for (SearchEmergencyLocationInfoItem it : items) {
            if (it.getHpid() == null) continue;
            CheckEmergencyRealtimeItem rti = rtMap.get(it.getHpid().trim());
            if (rti != null) {
                it.setHvec(rti.getHvec());
                it.setHvgc(rti.getHvgc());
            }
        }


        long matched = items.stream().filter(x -> x.getHvec() != null && !x.getHvec().trim().isEmpty()).count();
        log.info("[MERGE] totalItems={}, matchedHvec={}", items.size(), matched);

        return base;
    }



    private static class Stage {
        String stage1;
        String stage2;
        Stage(String s1, String s2) { this.stage1 = s1; this.stage2 = s2; }
    }


    private String normalizeStage1(String stage1) {
        if (stage1 == null) return null;
        String s = stage1.trim();
        if (s.isEmpty()) return null;

        if (s.equals("서울특별시")) return "서울";
        if (s.equals("부산광역시")) return "부산";
        if (s.equals("대구광역시")) return "대구";
        if (s.equals("인천광역시")) return "인천";
        if (s.equals("광주광역시")) return "광주";
        if (s.equals("대전광역시")) return "대전";
        if (s.equals("울산광역시")) return "울산";
        if (s.equals("세종특별자치시")) return "세종";
        if (s.equals("제주특별자치도")) return "제주";
        if (s.equals("경기도")) return "경기";
        if (s.equals("강원특별자치도") || s.equals("강원도")) return "강원";
        if (s.equals("충청북도")) return "충북";
        if (s.equals("충청남도")) return "충남";
        if (s.equals("전라북도")) return "전북";
        if (s.equals("전라남도")) return "전남";
        if (s.equals("경상북도")) return "경북";
        if (s.equals("경상남도")) return "경남";

        return s;
    }


    private String normalizeStage2(String stage2) {
        if (stage2 == null) return null;
        String s = stage2.trim();
        if (s.isEmpty()) return null;

        // 주소에서 나올 수 있는 괄호/쉼표 등 제거
        s = s.replaceAll("[,()]", "").trim();
        return s.isEmpty() ? null : s;
    }


    private Stage extractStage1Stage2(String dutyAddr) {
        if (dutyAddr == null) return new Stage(null, null);
        String a = dutyAddr.trim();
        if (a.isEmpty()) return new Stage(null, null);

        String[] parts = a.split("\\s+");
        if (parts.length < 2) return new Stage(null, null);

        String stage1 = parts[0].trim();
        String stage2 = parts[1].trim();


        if (parts.length >= 3) {
            String p2 = parts[1].trim();
            String p3 = parts[2].trim();
            if ((p2.endsWith("시") || p2.endsWith("군")) && p3.endsWith("구")) {
                stage2 = p2 + " " + p3;
            }
        }

        return new Stage(stage1, stage2);
    }


    private void mergeRealtimeWithFallback(String stage1, String stage2, Map<String, CheckEmergencyRealtimeItem> rtMap) {


        CheckEmergencyRealtimeResponse first = fetchRealtimeFirstPage(stage1, stage2, 1, 500);
        int totalCount1 = safeTotalCount(first);

        if (totalCount1 > 0) {
            log.debug("[RT] stage1={}, stage2={}, totalCount={}", stage1, stage2, totalCount1);
            mergeRealtimeAllPages(stage1, stage2, rtMap);
            return;
        }


        String fallback = stage2;
        if (stage2.contains(" ")) {
            fallback = stage2.split("\\s+")[0];
        }

        if (!Objects.equals(fallback, stage2)) {
            CheckEmergencyRealtimeResponse second = fetchRealtimeFirstPage(stage1, fallback, 1, 500);
            int totalCount2 = safeTotalCount(second);

            if (totalCount2 > 0) {
                log.debug("[RT-FB] stage1={}, stage2={} -> {}, totalCount={}", stage1, stage2, fallback, totalCount2);
                mergeRealtimeAllPages(stage1, fallback, rtMap);
            } else {
                log.debug("[RT-EMPTY] stage1={}, stage2={}, fallbackStage2={}, totalCount=0", stage1, stage2, fallback);
            }
        } else {
            log.debug("[RT-EMPTY] stage1={}, stage2={}, totalCount=0", stage1, stage2);
        }
    }


    private void mergeRealtimeAllPages(String stage1, String stage2, Map<String, CheckEmergencyRealtimeItem> rtMap) {
        int page = 1;
        int numOfRows = 500;
        int totalCount = Integer.MAX_VALUE;

        while ((page - 1) * numOfRows < totalCount) {
            CheckEmergencyRealtimeRequestDTO rtReq = new CheckEmergencyRealtimeRequestDTO();
            rtReq.setStage1(stage1);
            rtReq.setStage2(stage2);
            rtReq.setPageNo(page);
            rtReq.setNumOfRows(numOfRows);

            CheckEmergencyRealtimeResponse rt = checkEmergencyRealtimeService.getCheckEmergencyRealtimeResponse(rtReq);
            if (rt == null || rt.getBody() == null || rt.getBody().getItems() == null) break;

            totalCount = rt.getBody().getTotalCount();
            List<CheckEmergencyRealtimeItem> list = rt.getBody().getItems();

            if (list.isEmpty()) break;

            for (CheckEmergencyRealtimeItem rti : list) {
                if (rti.getHpid() == null) continue;
                rtMap.putIfAbsent(rti.getHpid().trim(), rti);
            }

            page++;


            if (page > 30) break;
        }
    }


    private CheckEmergencyRealtimeResponse fetchRealtimeFirstPage(String stage1, String stage2, int pageNo, int numOfRows) {
        try {
            CheckEmergencyRealtimeRequestDTO rtReq = new CheckEmergencyRealtimeRequestDTO();
            rtReq.setStage1(stage1);
            rtReq.setStage2(stage2);
            rtReq.setPageNo(pageNo);
            rtReq.setNumOfRows(numOfRows);
            return checkEmergencyRealtimeService.getCheckEmergencyRealtimeResponse(rtReq);
        } catch (Exception e) {
            return null;
        }
    }

    private int safeTotalCount(CheckEmergencyRealtimeResponse rt) {
        try {
            if (rt == null || rt.getBody() == null) return 0;
            return rt.getBody().getTotalCount();
        } catch (Exception e) {
            return 0;
        }
    }
}
