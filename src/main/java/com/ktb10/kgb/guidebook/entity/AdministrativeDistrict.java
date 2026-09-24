package com.ktb10.kgb.guidebook.entity;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 행정안전부 2026-08-31 행정구역 기준의 2단계 시군구입니다. */
public enum AdministrativeDistrict {
    REGION_11110(AdministrativeProvince.SEOUL, "11110", "종로구"),
    REGION_11140(AdministrativeProvince.SEOUL, "11140", "중구"),
    REGION_11170(AdministrativeProvince.SEOUL, "11170", "용산구"),
    REGION_11200(AdministrativeProvince.SEOUL, "11200", "성동구"),
    REGION_11215(AdministrativeProvince.SEOUL, "11215", "광진구"),
    REGION_11230(AdministrativeProvince.SEOUL, "11230", "동대문구"),
    REGION_11260(AdministrativeProvince.SEOUL, "11260", "중랑구"),
    REGION_11290(AdministrativeProvince.SEOUL, "11290", "성북구"),
    REGION_11305(AdministrativeProvince.SEOUL, "11305", "강북구"),
    REGION_11320(AdministrativeProvince.SEOUL, "11320", "도봉구"),
    REGION_11350(AdministrativeProvince.SEOUL, "11350", "노원구"),
    REGION_11380(AdministrativeProvince.SEOUL, "11380", "은평구"),
    REGION_11410(AdministrativeProvince.SEOUL, "11410", "서대문구"),
    REGION_11440(AdministrativeProvince.SEOUL, "11440", "마포구"),
    REGION_11470(AdministrativeProvince.SEOUL, "11470", "양천구"),
    REGION_11500(AdministrativeProvince.SEOUL, "11500", "강서구"),
    REGION_11530(AdministrativeProvince.SEOUL, "11530", "구로구"),
    REGION_11545(AdministrativeProvince.SEOUL, "11545", "금천구"),
    REGION_11560(AdministrativeProvince.SEOUL, "11560", "영등포구"),
    REGION_11590(AdministrativeProvince.SEOUL, "11590", "동작구"),
    REGION_11620(AdministrativeProvince.SEOUL, "11620", "관악구"),
    REGION_11650(AdministrativeProvince.SEOUL, "11650", "서초구"),
    REGION_11680(AdministrativeProvince.SEOUL, "11680", "강남구"),
    REGION_11710(AdministrativeProvince.SEOUL, "11710", "송파구"),
    REGION_11740(AdministrativeProvince.SEOUL, "11740", "강동구"),
    REGION_12110(AdministrativeProvince.JEONNAM_GWANGJU, "12110", "목포시"),
    REGION_12130(AdministrativeProvince.JEONNAM_GWANGJU, "12130", "여수시"),
    REGION_12150(AdministrativeProvince.JEONNAM_GWANGJU, "12150", "순천시"),
    REGION_12170(AdministrativeProvince.JEONNAM_GWANGJU, "12170", "나주시"),
    REGION_12190(AdministrativeProvince.JEONNAM_GWANGJU, "12190", "광양시"),
    REGION_12210(AdministrativeProvince.JEONNAM_GWANGJU, "12210", "동구"),
    REGION_12240(AdministrativeProvince.JEONNAM_GWANGJU, "12240", "서구"),
    REGION_12270(AdministrativeProvince.JEONNAM_GWANGJU, "12270", "남구"),
    REGION_12300(AdministrativeProvince.JEONNAM_GWANGJU, "12300", "북구"),
    REGION_12330(AdministrativeProvince.JEONNAM_GWANGJU, "12330", "광산구"),
    REGION_12710(AdministrativeProvince.JEONNAM_GWANGJU, "12710", "담양군"),
    REGION_12720(AdministrativeProvince.JEONNAM_GWANGJU, "12720", "곡성군"),
    REGION_12730(AdministrativeProvince.JEONNAM_GWANGJU, "12730", "구례군"),
    REGION_12740(AdministrativeProvince.JEONNAM_GWANGJU, "12740", "고흥군"),
    REGION_12750(AdministrativeProvince.JEONNAM_GWANGJU, "12750", "보성군"),
    REGION_12760(AdministrativeProvince.JEONNAM_GWANGJU, "12760", "화순군"),
    REGION_12770(AdministrativeProvince.JEONNAM_GWANGJU, "12770", "장흥군"),
    REGION_12780(AdministrativeProvince.JEONNAM_GWANGJU, "12780", "강진군"),
    REGION_12790(AdministrativeProvince.JEONNAM_GWANGJU, "12790", "해남군"),
    REGION_12800(AdministrativeProvince.JEONNAM_GWANGJU, "12800", "영암군"),
    REGION_12810(AdministrativeProvince.JEONNAM_GWANGJU, "12810", "무안군"),
    REGION_12820(AdministrativeProvince.JEONNAM_GWANGJU, "12820", "함평군"),
    REGION_12830(AdministrativeProvince.JEONNAM_GWANGJU, "12830", "영광군"),
    REGION_12840(AdministrativeProvince.JEONNAM_GWANGJU, "12840", "장성군"),
    REGION_12850(AdministrativeProvince.JEONNAM_GWANGJU, "12850", "완도군"),
    REGION_12860(AdministrativeProvince.JEONNAM_GWANGJU, "12860", "진도군"),
    REGION_12870(AdministrativeProvince.JEONNAM_GWANGJU, "12870", "신안군"),
    REGION_26110(AdministrativeProvince.BUSAN, "26110", "중구"),
    REGION_26140(AdministrativeProvince.BUSAN, "26140", "서구"),
    REGION_26170(AdministrativeProvince.BUSAN, "26170", "동구"),
    REGION_26200(AdministrativeProvince.BUSAN, "26200", "영도구"),
    REGION_26230(AdministrativeProvince.BUSAN, "26230", "부산진구"),
    REGION_26260(AdministrativeProvince.BUSAN, "26260", "동래구"),
    REGION_26290(AdministrativeProvince.BUSAN, "26290", "남구"),
    REGION_26320(AdministrativeProvince.BUSAN, "26320", "북구"),
    REGION_26350(AdministrativeProvince.BUSAN, "26350", "해운대구"),
    REGION_26380(AdministrativeProvince.BUSAN, "26380", "사하구"),
    REGION_26410(AdministrativeProvince.BUSAN, "26410", "금정구"),
    REGION_26440(AdministrativeProvince.BUSAN, "26440", "강서구"),
    REGION_26470(AdministrativeProvince.BUSAN, "26470", "연제구"),
    REGION_26500(AdministrativeProvince.BUSAN, "26500", "수영구"),
    REGION_26530(AdministrativeProvince.BUSAN, "26530", "사상구"),
    REGION_26710(AdministrativeProvince.BUSAN, "26710", "기장군"),
    REGION_27110(AdministrativeProvince.DAEGU, "27110", "중구"),
    REGION_27140(AdministrativeProvince.DAEGU, "27140", "동구"),
    REGION_27170(AdministrativeProvince.DAEGU, "27170", "서구"),
    REGION_27200(AdministrativeProvince.DAEGU, "27200", "남구"),
    REGION_27230(AdministrativeProvince.DAEGU, "27230", "북구"),
    REGION_27260(AdministrativeProvince.DAEGU, "27260", "수성구"),
    REGION_27290(AdministrativeProvince.DAEGU, "27290", "달서구"),
    REGION_27710(AdministrativeProvince.DAEGU, "27710", "달성군"),
    REGION_27720(AdministrativeProvince.DAEGU, "27720", "군위군"),
    REGION_28125(AdministrativeProvince.INCHEON, "28125", "제물포구"),
    REGION_28155(AdministrativeProvince.INCHEON, "28155", "영종구"),
    REGION_28177(AdministrativeProvince.INCHEON, "28177", "미추홀구"),
    REGION_28185(AdministrativeProvince.INCHEON, "28185", "연수구"),
    REGION_28200(AdministrativeProvince.INCHEON, "28200", "남동구"),
    REGION_28237(AdministrativeProvince.INCHEON, "28237", "부평구"),
    REGION_28245(AdministrativeProvince.INCHEON, "28245", "계양구"),
    REGION_28275(AdministrativeProvince.INCHEON, "28275", "서해구"),
    REGION_28290(AdministrativeProvince.INCHEON, "28290", "검단구"),
    REGION_28710(AdministrativeProvince.INCHEON, "28710", "강화군"),
    REGION_28720(AdministrativeProvince.INCHEON, "28720", "옹진군"),
    REGION_30110(AdministrativeProvince.DAEJEON, "30110", "동구"),
    REGION_30140(AdministrativeProvince.DAEJEON, "30140", "중구"),
    REGION_30170(AdministrativeProvince.DAEJEON, "30170", "서구"),
    REGION_30200(AdministrativeProvince.DAEJEON, "30200", "유성구"),
    REGION_30230(AdministrativeProvince.DAEJEON, "30230", "대덕구"),
    REGION_31110(AdministrativeProvince.ULSAN, "31110", "중구"),
    REGION_31140(AdministrativeProvince.ULSAN, "31140", "남구"),
    REGION_31170(AdministrativeProvince.ULSAN, "31170", "동구"),
    REGION_31200(AdministrativeProvince.ULSAN, "31200", "북구"),
    REGION_31710(AdministrativeProvince.ULSAN, "31710", "울주군"),
    REGION_36110(AdministrativeProvince.SEJONG, "36110", "세종특별자치시"),
    REGION_41111(AdministrativeProvince.GYEONGGI, "41111", "수원시 장안구"),
    REGION_41113(AdministrativeProvince.GYEONGGI, "41113", "수원시 권선구"),
    REGION_41115(AdministrativeProvince.GYEONGGI, "41115", "수원시 팔달구"),
    REGION_41117(AdministrativeProvince.GYEONGGI, "41117", "수원시 영통구"),
    REGION_41131(AdministrativeProvince.GYEONGGI, "41131", "성남시 수정구"),
    REGION_41133(AdministrativeProvince.GYEONGGI, "41133", "성남시 중원구"),
    REGION_41135(AdministrativeProvince.GYEONGGI, "41135", "성남시 분당구"),
    REGION_41150(AdministrativeProvince.GYEONGGI, "41150", "의정부시"),
    REGION_41171(AdministrativeProvince.GYEONGGI, "41171", "안양시 만안구"),
    REGION_41173(AdministrativeProvince.GYEONGGI, "41173", "안양시 동안구"),
    REGION_41192(AdministrativeProvince.GYEONGGI, "41192", "부천시 원미구"),
    REGION_41194(AdministrativeProvince.GYEONGGI, "41194", "부천시 소사구"),
    REGION_41196(AdministrativeProvince.GYEONGGI, "41196", "부천시 오정구"),
    REGION_41210(AdministrativeProvince.GYEONGGI, "41210", "광명시"),
    REGION_41220(AdministrativeProvince.GYEONGGI, "41220", "평택시"),
    REGION_41250(AdministrativeProvince.GYEONGGI, "41250", "동두천시"),
    REGION_41271(AdministrativeProvince.GYEONGGI, "41271", "안산시 상록구"),
    REGION_41273(AdministrativeProvince.GYEONGGI, "41273", "안산시 단원구"),
    REGION_41281(AdministrativeProvince.GYEONGGI, "41281", "고양시 덕양구"),
    REGION_41285(AdministrativeProvince.GYEONGGI, "41285", "고양시 일산동구"),
    REGION_41287(AdministrativeProvince.GYEONGGI, "41287", "고양시 일산서구"),
    REGION_41290(AdministrativeProvince.GYEONGGI, "41290", "과천시"),
    REGION_41310(AdministrativeProvince.GYEONGGI, "41310", "구리시"),
    REGION_41360(AdministrativeProvince.GYEONGGI, "41360", "남양주시"),
    REGION_41370(AdministrativeProvince.GYEONGGI, "41370", "오산시"),
    REGION_41390(AdministrativeProvince.GYEONGGI, "41390", "시흥시"),
    REGION_41410(AdministrativeProvince.GYEONGGI, "41410", "군포시"),
    REGION_41430(AdministrativeProvince.GYEONGGI, "41430", "의왕시"),
    REGION_41450(AdministrativeProvince.GYEONGGI, "41450", "하남시"),
    REGION_41461(AdministrativeProvince.GYEONGGI, "41461", "용인시 처인구"),
    REGION_41463(AdministrativeProvince.GYEONGGI, "41463", "용인시 기흥구"),
    REGION_41465(AdministrativeProvince.GYEONGGI, "41465", "용인시 수지구"),
    REGION_41480(AdministrativeProvince.GYEONGGI, "41480", "파주시"),
    REGION_41500(AdministrativeProvince.GYEONGGI, "41500", "이천시"),
    REGION_41550(AdministrativeProvince.GYEONGGI, "41550", "안성시"),
    REGION_41570(AdministrativeProvince.GYEONGGI, "41570", "김포시"),
    REGION_41591(AdministrativeProvince.GYEONGGI, "41591", "화성시 만세구"),
    REGION_41593(AdministrativeProvince.GYEONGGI, "41593", "화성시 효행구"),
    REGION_41595(AdministrativeProvince.GYEONGGI, "41595", "화성시 병점구"),
    REGION_41597(AdministrativeProvince.GYEONGGI, "41597", "화성시 동탄구"),
    REGION_41610(AdministrativeProvince.GYEONGGI, "41610", "광주시"),
    REGION_41630(AdministrativeProvince.GYEONGGI, "41630", "양주시"),
    REGION_41650(AdministrativeProvince.GYEONGGI, "41650", "포천시"),
    REGION_41670(AdministrativeProvince.GYEONGGI, "41670", "여주시"),
    REGION_41800(AdministrativeProvince.GYEONGGI, "41800", "연천군"),
    REGION_41820(AdministrativeProvince.GYEONGGI, "41820", "가평군"),
    REGION_41830(AdministrativeProvince.GYEONGGI, "41830", "양평군"),
    REGION_43111(AdministrativeProvince.CHUNGBUK, "43111", "청주시 상당구"),
    REGION_43112(AdministrativeProvince.CHUNGBUK, "43112", "청주시 서원구"),
    REGION_43113(AdministrativeProvince.CHUNGBUK, "43113", "청주시 흥덕구"),
    REGION_43114(AdministrativeProvince.CHUNGBUK, "43114", "청주시 청원구"),
    REGION_43130(AdministrativeProvince.CHUNGBUK, "43130", "충주시"),
    REGION_43150(AdministrativeProvince.CHUNGBUK, "43150", "제천시"),
    REGION_43720(AdministrativeProvince.CHUNGBUK, "43720", "보은군"),
    REGION_43730(AdministrativeProvince.CHUNGBUK, "43730", "옥천군"),
    REGION_43740(AdministrativeProvince.CHUNGBUK, "43740", "영동군"),
    REGION_43745(AdministrativeProvince.CHUNGBUK, "43745", "증평군"),
    REGION_43750(AdministrativeProvince.CHUNGBUK, "43750", "진천군"),
    REGION_43760(AdministrativeProvince.CHUNGBUK, "43760", "괴산군"),
    REGION_43770(AdministrativeProvince.CHUNGBUK, "43770", "음성군"),
    REGION_43800(AdministrativeProvince.CHUNGBUK, "43800", "단양군"),
    REGION_44131(AdministrativeProvince.CHUNGNAM, "44131", "천안시 동남구"),
    REGION_44133(AdministrativeProvince.CHUNGNAM, "44133", "천안시 서북구"),
    REGION_44150(AdministrativeProvince.CHUNGNAM, "44150", "공주시"),
    REGION_44180(AdministrativeProvince.CHUNGNAM, "44180", "보령시"),
    REGION_44200(AdministrativeProvince.CHUNGNAM, "44200", "아산시"),
    REGION_44210(AdministrativeProvince.CHUNGNAM, "44210", "서산시"),
    REGION_44230(AdministrativeProvince.CHUNGNAM, "44230", "논산시"),
    REGION_44250(AdministrativeProvince.CHUNGNAM, "44250", "계룡시"),
    REGION_44270(AdministrativeProvince.CHUNGNAM, "44270", "당진시"),
    REGION_44710(AdministrativeProvince.CHUNGNAM, "44710", "금산군"),
    REGION_44760(AdministrativeProvince.CHUNGNAM, "44760", "부여군"),
    REGION_44770(AdministrativeProvince.CHUNGNAM, "44770", "서천군"),
    REGION_44790(AdministrativeProvince.CHUNGNAM, "44790", "청양군"),
    REGION_44800(AdministrativeProvince.CHUNGNAM, "44800", "홍성군"),
    REGION_44810(AdministrativeProvince.CHUNGNAM, "44810", "예산군"),
    REGION_44825(AdministrativeProvince.CHUNGNAM, "44825", "태안군"),
    REGION_47111(AdministrativeProvince.GYEONGBUK, "47111", "포항시 남구"),
    REGION_47113(AdministrativeProvince.GYEONGBUK, "47113", "포항시 북구"),
    REGION_47130(AdministrativeProvince.GYEONGBUK, "47130", "경주시"),
    REGION_47150(AdministrativeProvince.GYEONGBUK, "47150", "김천시"),
    REGION_47170(AdministrativeProvince.GYEONGBUK, "47170", "안동시"),
    REGION_47190(AdministrativeProvince.GYEONGBUK, "47190", "구미시"),
    REGION_47210(AdministrativeProvince.GYEONGBUK, "47210", "영주시"),
    REGION_47230(AdministrativeProvince.GYEONGBUK, "47230", "영천시"),
    REGION_47250(AdministrativeProvince.GYEONGBUK, "47250", "상주시"),
    REGION_47280(AdministrativeProvince.GYEONGBUK, "47280", "문경시"),
    REGION_47290(AdministrativeProvince.GYEONGBUK, "47290", "경산시"),
    REGION_47730(AdministrativeProvince.GYEONGBUK, "47730", "의성군"),
    REGION_47750(AdministrativeProvince.GYEONGBUK, "47750", "청송군"),
    REGION_47760(AdministrativeProvince.GYEONGBUK, "47760", "영양군"),
    REGION_47770(AdministrativeProvince.GYEONGBUK, "47770", "영덕군"),
    REGION_47820(AdministrativeProvince.GYEONGBUK, "47820", "청도군"),
    REGION_47830(AdministrativeProvince.GYEONGBUK, "47830", "고령군"),
    REGION_47840(AdministrativeProvince.GYEONGBUK, "47840", "성주군"),
    REGION_47850(AdministrativeProvince.GYEONGBUK, "47850", "칠곡군"),
    REGION_47900(AdministrativeProvince.GYEONGBUK, "47900", "예천군"),
    REGION_47920(AdministrativeProvince.GYEONGBUK, "47920", "봉화군"),
    REGION_47930(AdministrativeProvince.GYEONGBUK, "47930", "울진군"),
    REGION_47940(AdministrativeProvince.GYEONGBUK, "47940", "울릉군"),
    REGION_48121(AdministrativeProvince.GYEONGNAM, "48121", "창원시 의창구"),
    REGION_48123(AdministrativeProvince.GYEONGNAM, "48123", "창원시 성산구"),
    REGION_48125(AdministrativeProvince.GYEONGNAM, "48125", "창원시 마산합포구"),
    REGION_48127(AdministrativeProvince.GYEONGNAM, "48127", "창원시 마산회원구"),
    REGION_48129(AdministrativeProvince.GYEONGNAM, "48129", "창원시 진해구"),
    REGION_48170(AdministrativeProvince.GYEONGNAM, "48170", "진주시"),
    REGION_48220(AdministrativeProvince.GYEONGNAM, "48220", "통영시"),
    REGION_48240(AdministrativeProvince.GYEONGNAM, "48240", "사천시"),
    REGION_48250(AdministrativeProvince.GYEONGNAM, "48250", "김해시"),
    REGION_48270(AdministrativeProvince.GYEONGNAM, "48270", "밀양시"),
    REGION_48310(AdministrativeProvince.GYEONGNAM, "48310", "거제시"),
    REGION_48330(AdministrativeProvince.GYEONGNAM, "48330", "양산시"),
    REGION_48720(AdministrativeProvince.GYEONGNAM, "48720", "의령군"),
    REGION_48730(AdministrativeProvince.GYEONGNAM, "48730", "함안군"),
    REGION_48740(AdministrativeProvince.GYEONGNAM, "48740", "창녕군"),
    REGION_48820(AdministrativeProvince.GYEONGNAM, "48820", "고성군"),
    REGION_48840(AdministrativeProvince.GYEONGNAM, "48840", "남해군"),
    REGION_48850(AdministrativeProvince.GYEONGNAM, "48850", "하동군"),
    REGION_48860(AdministrativeProvince.GYEONGNAM, "48860", "산청군"),
    REGION_48870(AdministrativeProvince.GYEONGNAM, "48870", "함양군"),
    REGION_48880(AdministrativeProvince.GYEONGNAM, "48880", "거창군"),
    REGION_48890(AdministrativeProvince.GYEONGNAM, "48890", "합천군"),
    REGION_50110(AdministrativeProvince.JEJU, "50110", "제주시"),
    REGION_50130(AdministrativeProvince.JEJU, "50130", "서귀포시"),
    REGION_51110(AdministrativeProvince.GANGWON, "51110", "춘천시"),
    REGION_51130(AdministrativeProvince.GANGWON, "51130", "원주시"),
    REGION_51150(AdministrativeProvince.GANGWON, "51150", "강릉시"),
    REGION_51170(AdministrativeProvince.GANGWON, "51170", "동해시"),
    REGION_51190(AdministrativeProvince.GANGWON, "51190", "태백시"),
    REGION_51210(AdministrativeProvince.GANGWON, "51210", "속초시"),
    REGION_51230(AdministrativeProvince.GANGWON, "51230", "삼척시"),
    REGION_51720(AdministrativeProvince.GANGWON, "51720", "홍천군"),
    REGION_51730(AdministrativeProvince.GANGWON, "51730", "횡성군"),
    REGION_51750(AdministrativeProvince.GANGWON, "51750", "영월군"),
    REGION_51760(AdministrativeProvince.GANGWON, "51760", "평창군"),
    REGION_51770(AdministrativeProvince.GANGWON, "51770", "정선군"),
    REGION_51780(AdministrativeProvince.GANGWON, "51780", "철원군"),
    REGION_51790(AdministrativeProvince.GANGWON, "51790", "화천군"),
    REGION_51800(AdministrativeProvince.GANGWON, "51800", "양구군"),
    REGION_51810(AdministrativeProvince.GANGWON, "51810", "인제군"),
    REGION_51820(AdministrativeProvince.GANGWON, "51820", "고성군"),
    REGION_51830(AdministrativeProvince.GANGWON, "51830", "양양군"),
    REGION_52111(AdministrativeProvince.JEONBUK, "52111", "전주시 완산구"),
    REGION_52113(AdministrativeProvince.JEONBUK, "52113", "전주시 덕진구"),
    REGION_52130(AdministrativeProvince.JEONBUK, "52130", "군산시"),
    REGION_52140(AdministrativeProvince.JEONBUK, "52140", "익산시"),
    REGION_52180(AdministrativeProvince.JEONBUK, "52180", "정읍시"),
    REGION_52190(AdministrativeProvince.JEONBUK, "52190", "남원시"),
    REGION_52210(AdministrativeProvince.JEONBUK, "52210", "김제시"),
    REGION_52710(AdministrativeProvince.JEONBUK, "52710", "완주군"),
    REGION_52720(AdministrativeProvince.JEONBUK, "52720", "진안군"),
    REGION_52730(AdministrativeProvince.JEONBUK, "52730", "무주군"),
    REGION_52740(AdministrativeProvince.JEONBUK, "52740", "장수군"),
    REGION_52750(AdministrativeProvince.JEONBUK, "52750", "임실군"),
    REGION_52770(AdministrativeProvince.JEONBUK, "52770", "순창군"),
    REGION_52790(AdministrativeProvince.JEONBUK, "52790", "고창군"),
    REGION_52800(AdministrativeProvince.JEONBUK, "52800", "부안군");

    private static final Map<String, AdministrativeDistrict> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(
                    AdministrativeDistrict::code,
                    Function.identity()));

    private final AdministrativeProvince province;
    private final String code;
    private final String displayName;

    AdministrativeDistrict(
            AdministrativeProvince province,
            String code,
            String displayName) {
        this.province = province;
        this.code = code;
        this.displayName = displayName;
    }

    public AdministrativeProvince province() {
        return province;
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    /** 서비스의 2단계 선택값입니다. 일반 시 산하 구는 시 단위로 합칩니다. */
    public String serviceName() {
        int nestedDistrictSeparator = displayName.indexOf(' ');
        if (nestedDistrictSeparator < 0) {
            return displayName;
        }
        return displayName.substring(0, nestedDistrictSeparator);
    }

    public static AdministrativeDistrict fromCode(String code) {
        AdministrativeDistrict district = BY_CODE.get(code);
        if (district == null) {
            throw new IllegalArgumentException("지원하지 않는 시군구 코드입니다: " + code);
        }
        return district;
    }

    public static AdministrativeDistrict fromDisplayName(
            AdministrativeProvince province,
            String displayName) {
        return Arrays.stream(values())
                .filter(district -> district.province == province)
                .filter(district -> district.serviceName().equals(displayName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "지원하지 않는 시도·시군구 조합입니다: "
                                + province.displayName() + " " + displayName));
    }

    public static List<AdministrativeDistrict> findAllByProvince(
            AdministrativeProvince province) {
        return Arrays.stream(values())
                .filter(district -> district.province == province)
                .toList();
    }

    public static List<String> findServiceNamesByProvince(
            AdministrativeProvince province) {
        return Arrays.stream(values())
                .filter(district -> district.province == province)
                .map(AdministrativeDistrict::serviceName)
                .distinct()
                .toList();
    }
}
