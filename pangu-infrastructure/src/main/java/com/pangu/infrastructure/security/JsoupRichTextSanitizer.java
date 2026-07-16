// 关联业务：按微信小程序 RichText 的保守可信标签集清洗维修实施方案正文。
package com.pangu.infrastructure.security;

import com.pangu.domain.gateway.RichTextSanitizer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class JsoupRichTextSanitizer implements RichTextSanitizer {

    /** 图片只保留后端签发的稳定 imageId，不接收外部地址、事件属性或行内样式。 */
    private static final Safelist MINI_PROGRAM_SAFELIST = Safelist.none().addTags(
            "p", "br", "strong", "b", "em", "i", "u",
            "ul", "ol", "li", "h3", "h4", "blockquote", "img")
            .addAttributes("img", "data-repair-image-id", "alt");
    private static final Document.OutputSettings OUTPUT_SETTINGS = new Document.OutputSettings()
            .prettyPrint(false);

    @Override
    public SanitizedRichText sanitize(String source) {
        if (source == null || source.isBlank()) {
            return new SanitizedRichText("", "", Set.of());
        }
        String cleaned = Jsoup.clean(source.trim(), "", MINI_PROGRAM_SAFELIST, OUTPUT_SETTINGS).trim();
        Document fragment = Jsoup.parseBodyFragment(cleaned);
        fragment.outputSettings(OUTPUT_SETTINGS);
        Set<Long> imageIds = new LinkedHashSet<>();
        for (Element image : fragment.select("img")) {
            String rawId = image.attr("data-repair-image-id");
            if (!rawId.matches("[1-9]\\d*")) {
                image.remove();
                continue;
            }
            imageIds.add(Long.valueOf(rawId));
        }
        String html = fragment.body().html().trim();
        String plainText = fragment.body().text().trim();
        return new SanitizedRichText(html, plainText, imageIds);
    }
}
