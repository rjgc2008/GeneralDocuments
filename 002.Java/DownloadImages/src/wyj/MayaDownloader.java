package wyj;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class MayaDownloader {

    // ========== 配置区 ==========

    // 若站点需要 cookie（例如绕过登录/风控），把浏览器里复制的整串 Cookie 放这里
    private static final String COOKIE =
            "cdb_cookietime=2592000; cdb_auth=bgGF5CwcYz2Wo%2FbF7VX9gmOIdBfdA1tq25JwOCnpZ%2FwgC35UI1dyTBja2qM0bt6SSw; " +
            "cdb_sid=5KC5wk; cdb_visitedfid=5D55D46D79D84D57D65D11; cdb_fid5=1761186434; " +
            "cdb_oldtopics=D2400784D2400776D2401666D2401667D";

    private static final String BASE = "http://www.tonemex.com/";      // 站点基址与默认 Referer
    private static final int START_NUM = 1;                             // 起始列表页
    private static final int END_NUM   = 50;                             // 结束列表页（含）
    private static final String SAVE_DIR = "E:/100-WYJ/999-TMP/201-Picture/"; // 保存目录（末尾保留 /）

    
    // 代理设置（如不需要代理，把 PROXY_HOST 设为 null 或 ""）
    private static final String PROXY_HOST = "172.16.208.2";
    private static final int PROXY_PORT = 7890;
    private static final boolean PROXY_USE_SOCKS = false; // true→SOCKS5，false→HTTP
    
    // 下载策略
    private static final int IMG_DOWNLOAD_RETRIES = 3;
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 20000;
    
    // 仅允许常见图片后缀（减少误抓广告资源）
    private static final boolean IMAGE_SUFFIX_WHITELIST = true;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36";
    // ========== 结束配置区 ==========

    public static void main(String[] args) {
        try {
            Files.createDirectories(Paths.get(SAVE_DIR));
        } catch (IOException e) {
            System.err.println("创建保存目录失败: " + e.getMessage());
            return;
        }

        if (PROXY_USE_SOCKS && PROXY_HOST != null && !PROXY_HOST.isEmpty()) {
            System.setProperty("socksProxyHost", PROXY_HOST);
            System.setProperty("socksProxyPort", String.valueOf(PROXY_PORT));
        }

        System.out.println("开始抓取，保存目录: " + SAVE_DIR);

        for (int page = START_NUM; page <= END_NUM; page++) {
            String forumPageUrl = BASE + "forumdisplay.php?fid=5&page=" + page;
            System.out.println("\n处理中，当前主页的链接是: " + forumPageUrl);
            try {
                Document forumDoc = fetchDocumentWithJsoup(forumPageUrl);

                if (isCloudflareChallenge(forumDoc)) {
                    System.err.println("检测到 Cloudflare/挑战页：请在浏览器通过验证并复制有效 Cookie（保持同 UA/同出口IP/代理）。");
                    continue;
                }

                Set<String> threadLinks = extractThreadLinks(forumDoc);
                System.out.println("第 " + page + " 页检测到主题链接: " + threadLinks.size());
                int count = 0;
                for (String threadUrl : threadLinks) {
                    count++;
                    System.out.println("  [" + count + "] 主题: " + threadUrl);
                    processPost(threadUrl); // 进入每个子页
                    sleepMillis(700 + new Random().nextInt(400)); // 轻限速
                }
            } catch (Exception ex) {
                System.err.println("页面抓取失败: " + forumPageUrl + " -> " + ex.getMessage());
            }
            sleepMillis(800 + new Random().nextInt(600));
        }

        System.out.println("全部完成。");
    }

    /** Jsoup 拉取页面，自动编码、重定向、UA/Cookie/Referrer */
    private static Document fetchDocumentWithJsoup(String url) throws IOException {
        org.jsoup.Connection conn = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(CONNECT_TIMEOUT_MS)
                .maxBodySize(0)
                .ignoreHttpErrors(true)
                .followRedirects(true)
                .referrer(BASE)
                .header("DNT", "1")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Cache-Control", "max-age=0")
                .header("Upgrade-Insecure-Requests", "1");

        if (COOKIE != null && !COOKIE.isEmpty()) conn.header("Cookie", COOKIE);
        if (PROXY_HOST != null && !PROXY_HOST.isEmpty() && !PROXY_USE_SOCKS) {
            conn.proxy(PROXY_HOST, PROXY_PORT);
        }
        return conn.get();
    }

    /** 识别 Cloudflare “Just a moment…” 等挑战页 */
    private static boolean isCloudflareChallenge(Document doc) {
        if (doc == null) return true;
        String title = doc.title() == null ? "" : doc.title().toLowerCase(Locale.ROOT);
        if (title.contains("just a moment")) return true;
        if (!doc.select("script[src*=/cdn-cgi/challenge-platform/],script:contains(_cf_chl_opt)").isEmpty()) return true;
        if (!doc.select("noscript #challenge-error-text").isEmpty()) return true;
        return false;
    }

    /** 列表页抽取主题链接：Discuz! 5.5 的 viewthread.php?tid=...，兼容 thread-*.html */
    private static Set<String> extractThreadLinks(Document forumDoc) {
        Set<String> out = new LinkedHashSet<>();
        if (forumDoc == null) return out;

        // 1) 标准：viewthread.php?tid=xxxx
        Elements a1 = forumDoc.select("a[href^=viewthread.php?tid=]");
        for (Element a : a1) {
            String abs = a.attr("abs:href");
            if (abs == null || abs.isEmpty()) abs = makeAbsolute(BASE, a.attr("href"));
            out.add(abs);
        }

        // 2) 兜底扫描
        Elements anchors = forumDoc.select("a[href]");
        for (Element a : anchors) {
            String href = a.attr("href");
            if (href == null) continue;

            if (href.contains("viewthread.php?tid=")) {
                String abs = a.attr("abs:href");
                if (abs == null || abs.isEmpty()) abs = makeAbsolute(BASE, href);
                out.add(abs);
            }
            if (href.startsWith("thread-") && href.endsWith(".html")) {
                String abs = a.attr("abs:href");
                if (abs == null || abs.isEmpty()) abs = makeAbsolute(BASE, href);
                out.add(abs);
            }
        }
        return out;
    }

    /** 处理帖子页：抽取图片 URL 并下载 */
    private static void processPost(String postUrl) {
        try {
            Document postDoc = fetchDocumentWithJsoup(postUrl);
            if (postDoc == null) {
                System.err.println("无法解析帖子: " + postUrl);
                return;
            }
            if (isCloudflareChallenge(postDoc)) {
                System.err.println("帖子页出现 Cloudflare/挑战，跳过：" + postUrl);
                return;
            }

            Set<String> imgUrls = extractImageUrls(postDoc);
            if (IMAGE_SUFFIX_WHITELIST) {
                imgUrls.removeIf(u -> !u.matches("(?i)^https?://.+\\.(jpg|jpeg|png|gif|webp)(\\?.*)?$"));
            }

            if (imgUrls.isEmpty()) {
                System.out.println("    无图片");
                return;
            }

            String baseName = generateBaseName(postDoc, postUrl);
            int idx = 0;
            for (String imgUrl : imgUrls) {
                idx++;
                String ext = guessExtensionFromUrl(imgUrl);
                if (ext == null) ext = "jpg";
                String filename = (idx == 1) ? (baseName + "." + ext) : (baseName + "_" + idx + "." + ext);
                Path savePath = Paths.get(SAVE_DIR, filename);
                downloadWithSmartReferer(imgUrl, savePath.toString(), postUrl);
                sleepMillis(300 + new Random().nextInt(300));
            }
        } catch (Exception e) {
            System.err.println("处理失败: " + postUrl + " -> " + e.getMessage());
        }
    }

    /**
     * 抽取图片：
     *  - Discuz 帖子正文常见容器：div#postmessage_*, 也有模板使用 div#message*, 或 class="t_msgfont"
     *  - 同时兼容 a img 及懒加载属性
     */
    private static Set<String> extractImageUrls(Document doc) {
        Set<String> urls = new LinkedHashSet<>();
        if (doc == null) return urls;

        // 1) 直接的 IMG（覆盖你提供的 HTML：div.t_msgfont 内的 <img src="https://*.l3n.co/...">）
        Elements imgs = doc.select(
                "div[id^=postmessage_] img, " +
                "div[id^=message] img, " +
                ".t_msgfont img, " +
                "div[id^=postmessage_] a img, " +
                "div[id^=message] a img, " +
                ".t_msgfont a img"
        );

        for (Element img : imgs) {
            String u = "";
            if (img.hasAttr("src")) u = img.attr("abs:src");
            if (isEmpty(u) && img.hasAttr("data-src")) u = img.attr("abs:data-src");
            if (isEmpty(u) && img.hasAttr("data-file")) u = img.attr("abs:data-file");
            if (isEmpty(u) && img.hasAttr("zoomfile")) u = img.attr("abs:zoomfile");
            if (isEmpty(u) && img.hasAttr("file")) u = img.attr("abs:file");
            if (isEmpty(u)) {
                Element parent = img.parent();
                if (parent != null && parent.tagName().equalsIgnoreCase("a") && parent.hasAttr("href")) {
                    u = parent.attr("abs:href");
                }
            }
            if (!isEmpty(u) && (u.startsWith("http://") || u.startsWith("https://"))) {
                urls.add(u);
            }
        }

        // 2) 备选：正文中可能直接给出 a[href] 指向图片/附件
        Elements aImgs = doc.select(".t_msgfont a[href], div[id^=postmessage_] a[href], div[id^=message] a[href]");
        for (Element a : aImgs) {
            String href = a.attr("abs:href");
            if (!isEmpty(href) &&
                (href.matches("(?i).+\\.(jpg|jpeg|png|gif|webp)(\\?.*)?$")
                        || href.contains("attachment")
                        || href.contains("image"))) {
                urls.add(href);
            }
        }

        return urls;
    }

    /**
     * 智能 Referer 重试下载：
     * 依次尝试：帖子页 URL → 图片域名根 → 论坛 BASE → 无 Referer
     */
    private static void downloadWithSmartReferer(String imgUrl, String savePath, String postUrl) {
        File f = new File(savePath);
        if (f.exists()) {
            System.out.println("    已存在，跳过: " + savePath);
            return;
        }

        List<String> referers = new ArrayList<>();
        referers.add(postUrl);
        referers.add(originOf(imgUrl));
        referers.add(BASE);
        referers.add(""); // 空 Referer

        int totalAttempts = IMG_DOWNLOAD_RETRIES * referers.size();
        int attempt = 0;

        for (int r = 0; r < referers.size(); r++) {
            String ref = referers.get(r);
            for (int round = 1; round <= IMG_DOWNLOAD_RETRIES; round++) {
                attempt++;
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(imgUrl);
                    Proxy proxy = buildProxyIfNeeded();
                    conn = (HttpURLConnection) (proxy != null ? url.openConnection(proxy) : url.openConnection());
                    conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                    conn.setReadTimeout(READ_TIMEOUT_MS);
                    conn.setInstanceFollowRedirects(true);
                    conn.setRequestProperty("User-Agent", USER_AGENT);
                    conn.setRequestProperty("Accept", "image/avif,image/webp,image/*,*/*;q=0.8");
                    if (!isEmpty(ref)) conn.setRequestProperty("Referer", ref);
                    if (!COOKIE.isEmpty()) conn.setRequestProperty("Cookie", COOKIE);

                    int code = conn.getResponseCode();
                    if (code >= 300 && code < 400) {
                        String loc = conn.getHeaderField("Location");
                        if (!isEmpty(loc)) {
                            imgUrl = makeAbsolute(url.toString(), loc);
                            System.out.println("    3xx 重定向到: " + imgUrl);
                            // 继续使用同一 referer 重试本 round
                            conn.disconnect();
                            round--; // 不消耗本 referer 的次数
                            continue;
                        }
                    }
                    if (code < 200 || code >= 300) {
                        throw new IOException("HTTP 非 2xx 响应: " + code + " (Referer=" + (isEmpty(ref) ? "EMPTY" : ref) + ")");
                    }

                    String ctype = conn.getContentType();
                    if (ctype == null || !ctype.toLowerCase(Locale.ROOT).startsWith("image/")) {
                        throw new IOException("非图片 Content-Type: " + ctype + " (Referer=" + (isEmpty(ref) ? "EMPTY" : ref) + ")");
                    }

                    try (InputStream in = conn.getInputStream();
                         OutputStream out = new FileOutputStream(savePath)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                    }

                    System.out.println("    下载成功: " + savePath + "  [Referer=" + (isEmpty(ref) ? "EMPTY" : ref) + "]");
                    return;
                } catch (IOException e) {
                    System.err.println("    下载失败 (" + attempt + "/" + totalAttempts + "): " + imgUrl +
                            " -> " + e.getMessage());
                    sleepMillis(400L * attempt);
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }

        System.err.println("    多次尝试后仍失败: " + imgUrl);
    }

    /** 生成安全且尽可能唯一的文件基名：清洗标题 + 提取 thread id */
    private static String generateBaseName(Document doc, String url) {
        String title = (doc != null && doc.title() != null) ? doc.title().trim() : "";
        title = title.replaceAll("\\s+", " ").replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (title.length() > 60) title = title.substring(0, 60);

        String tid = extractThreadIdFromUrl(url);
        String base = (title.isEmpty() ? "untitled" : title) + "_" + tid;
        base = base.replaceAll("[\\p{Cntrl}]", "_");
        return base;
    }

    /** 兼容 viewthread.php?tid=XXXX 与 thread-<id>-1-1.html */
    private static String extractThreadIdFromUrl(String url) {
        if (url == null) return "thread";

        int t = url.indexOf("viewthread.php?tid=");
        if (t >= 0) {
            String sub = url.substring(t + "viewthread.php?tid=".length());
            int amp = sub.indexOf('&');
            String tid = (amp >= 0 ? sub.substring(0, amp) : sub);
            tid = tid.replaceAll("[^0-9]", "");
            if (!tid.isEmpty()) return "tid-" + tid;
        }

        int p = url.indexOf("thread-");
        if (p >= 0) {
            int q = url.indexOf(".html", p);
            if (q > p) return url.substring(p, q).replaceAll("[^0-9a-zA-Z-_]", "_");
            return url.substring(p).replaceAll("[^0-9a-zA-Z-_]", "_");
        }

        return "t" + Math.abs(url.hashCode());
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String guessExtensionFromUrl(String u) {
        if (isEmpty(u)) return null;
        String lc = u.toLowerCase(Locale.ROOT);
        if (lc.contains(".jpg") || lc.contains(".jpeg")) return "jpg";
        if (lc.contains(".png")) return "png";
        if (lc.contains(".gif")) return "gif";
        if (lc.contains(".webp")) return "webp";
        return null;
    }

    private static Proxy buildProxyIfNeeded() {
        if (PROXY_HOST == null || PROXY_HOST.isEmpty()) return null;
        InetSocketAddress addr = new InetSocketAddress(PROXY_HOST, PROXY_PORT);
        return PROXY_USE_SOCKS ? new Proxy(Proxy.Type.SOCKS, addr) : new Proxy(Proxy.Type.HTTP, addr);
    }

    private static String makeAbsolute(String base, String relative) {
        try {
            URL baseUrl = new URL(base);
            return new URL(baseUrl, relative).toString();
        } catch (MalformedURLException e) {
            return relative;
        }
    }

    /** 提取 URL 的协议+主机（用于 Referer 退而求其次） */
    private static String originOf(String u) {
        try {
            URL url = new URL(u);
            String port = (url.getPort() == -1) ? "" : (":" + url.getPort());
            return url.getProtocol() + "://" + url.getHost() + port + "/";
        } catch (MalformedURLException e) {
            return "";
        }
    }

    private static void sleepMillis(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
