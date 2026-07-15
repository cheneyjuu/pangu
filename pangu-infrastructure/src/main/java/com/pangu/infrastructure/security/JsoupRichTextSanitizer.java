// 关联业务：按微信小程序 RichText 的保守可信标签集清洗维修实施方案正文。
package com.pangu.infrastructure.security;

import com.pangu.domain.gateway.RichTextSanitizer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

@Component
public class JsoupRichTextSanitizer implements RichTextSanitizer {

    /** 不允许任何属性，避免事件处理器、链接协议和行内样式成为第二条内容通道。 */
    private static final Safelist MINI_PROGRAM_SAFELIST = Safelist.none().addTags(
            "p", "br", "strong", "b", "em", "i", "u",
            "ul", "ol", "li", "h3", "h4", "blockquote");
    private static final Document.OutputSettings OUTPUT_SETTINGS = new Document.OutputSettings()
            .prettyPrint(false);

    @Override
    public SanitizedRichText sanitize(String source) {
        if (source == null || source.isBlank()) {
            return new SanitizedRichText("", "");
        }
        String html = Jsoup.clean(source.trim(), "", MINI_PROGRAM_SAFELIST, OUTPUT_SETTINGS).trim();
        String plainText = Jsoup.parseBodyFragment(html).body().text().trim();
        return new SanitizedRichText(html, plainText);
    }
}
