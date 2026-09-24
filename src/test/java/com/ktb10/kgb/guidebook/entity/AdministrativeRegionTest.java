package com.ktb10.kgb.guidebook.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AdministrativeRegionTest {

    @Test
    void providesSixteenProvinces() {
        assertThat(AdministrativeProvince.values()).hasSize(16);
        assertThat(AdministrativeProvince.fromCode("47"))
                .isEqualTo(AdministrativeProvince.GYEONGBUK);
        assertThat(AdministrativeProvince.GYEONGBUK.displayName()).isEqualTo("경상북도");
    }

    @Test
    void findsDistrictAndItsProvinceByOfficialCode() {
        AdministrativeDistrict district = AdministrativeDistrict.fromCode("47130");

        assertThat(district.displayName()).isEqualTo("경주시");
        assertThat(district.province()).isEqualTo(AdministrativeProvince.GYEONGBUK);
    }

    @Test
    void returnsDistrictsForSelectedProvince() {
        assertThat(AdministrativeDistrict.findAllByProvince(AdministrativeProvince.JEJU))
                .extracting(AdministrativeDistrict::displayName)
                .containsExactly("제주시", "서귀포시");
    }

    @Test
    void sejongUsesSameNameForRequiredSecondDepth() {
        assertThat(AdministrativeDistrict.findAllByProvince(AdministrativeProvince.SEJONG))
                .singleElement()
                .extracting(AdministrativeDistrict::displayName)
                .isEqualTo("세종특별자치시");
    }

    @Test
    void findsDistrictByProvinceAndDisplayName() {
        assertThat(AdministrativeDistrict.fromDisplayName(
                        AdministrativeProvince.GYEONGBUK,
                        "경주시"))
                .isEqualTo(AdministrativeDistrict.REGION_47130);
    }

    @Test
    void collapsesCityDistrictsToTwoDepthServiceNames() {
        assertThat(AdministrativeDistrict.findServiceNamesByProvince(
                        AdministrativeProvince.GYEONGGI))
                .contains("수원시", "성남시", "연천군")
                .doesNotContain("수원시 장안구");
        assertThat(AdministrativeDistrict.fromDisplayName(
                        AdministrativeProvince.GYEONGGI,
                        "수원시"))
                .extracting(AdministrativeDistrict::serviceName)
                .isEqualTo("수원시");
    }

    @Test
    void usesUniqueOfficialCodes() {
        assertThat(Arrays.stream(AdministrativeDistrict.values())
                .map(AdministrativeDistrict::code))
                .doesNotHaveDuplicates();
    }

    @Test
    void rejectsUnknownCodes() {
        assertThatThrownBy(() -> AdministrativeProvince.fromCode("99"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AdministrativeDistrict.fromCode("99999"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
