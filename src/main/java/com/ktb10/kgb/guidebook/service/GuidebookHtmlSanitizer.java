package com.ktb10.kgb.guidebook.service;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/** AI가 생성한 HTML에서 실행 가능한 요소와 위험 URL을 제거합니다. */
@Component
public class GuidebookHtmlSanitizer {

    private static final Safelist VIEWER_SAFELIST = Safelist.relaxed()
            .addTags("article", "section", "header", "footer", "main", "figure", "figcaption")
            .addAttributes(":all", "class", "id")
            .removeProtocols("a", "href", "ftp")
            .addEnforcedAttribute("a", "rel", "noopener noreferrer");

    public String sanitize(String html) {
        return Jsoup.clean(html, VIEWER_SAFELIST);
    }
}
