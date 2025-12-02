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

public class Sis001PosterDownloaderFixed {

    // ========== 配置区 ==========

    // 若站点需要 cookie（例如绕过 Cloudflare），把**浏览器开发者工具中复制的整串 Cookie**放这里（含 cf_clearance）
    private static final String COOKIE = "cdb2_sid=7LQ19Z; cdb2_uvStat=1761177525; cdb2_fuvs=230.229; cf_clearance=AWPeseOnSWLVpDUPcjw26U5oQ5KUmVGa4V5ZhYOCtpA-1761198911-1.2.1.1-LhierE6U1p17vtNpEa.UQnpST5Rf4fAHvbXE_AvG9cEmT5vdU.m9idaPNw6okE5o_sYKDtULmRasTSuyg9i1_APqF3kZQWCgMguf0W7R5162qjc9kqIi2duMvFeOVdmbS386U6Y7cMlhcJYL4tAHkVzkZXATj1UGF.D6XnCMOclrbAdfvkd0ZimRKeAPsRg5zbQAEgo8zFbX9JD6pdSQhhkj1t0FyVSDdSnvqTe0ua8";

    private static final int START_NUM = 10;          // 起始页
    private static final int END_NUM = 10;            // 结束页（包含）
    private static final String SAVE_DIR = "E:/100-WYJ/999-TMP/200-Picture/"; // 保存目录
    
    // 代理设置（如果不需要代理，把 PROXY_HOST 设为 null 或空字符串）
    private static final String PROXY_HOST = "172.16.208.2"; // 若不使用代理，设 null 或 ""
    private static final int PROXY_PORT = 7890;
    private static final boolean PROXY_USE_SOCKS = false; // true -> SOCKS5 (7891 等)，false -> HTTP
    
    // 下载策略
    private static final int IMG_DOWNLOAD_RETRIES = 3;
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 20000;
    
    //模拟浏览器的参数
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36";

    // ========== 结束配置区 ==========

    public static void main(String[] args) {

        //确认存放照片的目录是否存在
        try {
            Files.createDirectories(Paths.get(SAVE_DIR));
        } catch (IOException e) {
            System.err.println("创建保存目录失败: " + e.getMessage());
            return;
        }

        // 如需 SOCKS 代理且用 Jsoup（其 API 不直接支持 SOCKS），可以用 JVM 参数或这里设置系统属性：
        if (PROXY_USE_SOCKS && PROXY_HOST != null && !PROXY_HOST.isEmpty()) {
            System.setProperty("socksProxyHost", PROXY_HOST);
            System.setProperty("socksProxyPort", String.valueOf(PROXY_PORT));
        }

        System.out.println("开始抓取，保存目录，目录是: " + SAVE_DIR);

        for (int page = START_NUM; page <= END_NUM; page++) {
            String forumPageUrl = "https://sis001.com/forum/forum-230-" + page + ".html";
            System.out.println("\n第" + page + " 页正在检测，处理链接是: " + forumPageUrl);
            try {
                Document forumDoc = fetchDocumentWithJsoup(forumPageUrl);//根据URL发起请求

                // —— Cloudflare 挑战检测 —— //
                if (isCloudflareChallenge(forumDoc)) {
                    System.err.println("检测到 Cloudflare 挑战页：请在浏览器中通过验证并复制有效 Cookie 到 COOKIE 常量（且保持相同UA/同一出口IP/代理）。");
                    continue;
                }

                // 调试输出（可保留/可注释）
                // System.out.println("内容：" + forumDoc.outerHtml());

                Set<String> threadLinks = extractThreadLinks(forumDoc);
                System.out.println("第 " + page + " 页检测到网址总数是: " + threadLinks.size());
                int count = 0;
                for (String threadUrl : threadLinks) {
                    count++;
                    System.out.println(" 第 " + page + " 主页的 -> [ " + count + " / " + threadLinks.size() + "]子页的网址: " + threadUrl);
                    processPost(threadUrl);
                    sleepMillis(700 + new Random().nextInt(400)); // 轻限速
                }
            } catch (Exception ex) {
                System.err.println("页面抓取失败，网址是： " + forumPageUrl + "    错误信息是-> " + ex.getMessage());
            }
            sleepMillis(800 + new Random().nextInt(600));
        }

        System.out.println("全部完成。");
    }

    /**
     * 使用 Jsoup 连接并自动处理编码与 gzip，设置超时与 UA、可选 Cookie/Referrer
     * @param url
     * @return
     * @throws IOException
     */
    private static Document fetchDocumentWithJsoup(String url) throws IOException {
        org.jsoup.Connection conn = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(CONNECT_TIMEOUT_MS)
                .ignoreHttpErrors(true)    // 403/5xx 仍可查看返回（便于识别挑战/错误页）
                .followRedirects(true)
                .referrer("https://sis001.com/")
                .header("DNT", "1")
                // 不要显式声明 br/zstd（避免 zstd 压缩体）；不设 Accept-Encoding 让库自行决定
                .header("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Cache-Control", "max-age=0")
                .header("Upgrade-Insecure-Requests", "1");

        if (COOKIE != null && !COOKIE.isEmpty()) conn.header("Cookie", COOKIE);

        // Jsoup 支持 HTTP 代理
        if (PROXY_HOST != null && !PROXY_HOST.isEmpty() && !PROXY_USE_SOCKS) {
            conn.proxy(PROXY_HOST, PROXY_PORT);
        }

        return conn.get();
    }

    /**
     * 识别 Cloudflare “Just a moment…” 等挑战页
     * @param doc
     * @return
     */
    private static boolean isCloudflareChallenge(Document doc) {
        if (doc == null) return true;
        String title = doc.title() == null ? "" : doc.title().toLowerCase(Locale.ROOT);
        if (title.contains("just a moment")) return true;
        if (!doc.select("script[src*=/cdn-cgi/challenge-platform/],script:contains(_cf_chl_opt)").isEmpty()) return true;
        if (!doc.select("noscript #challenge-error-text").isEmpty()) return true;
        return false;
    }

    /**
     * 抽取网页链接（相对/绝对都支持，优先 thread-... 模式），只过滤"thread-<id>-1-1.html"格式的
     * @param forumDoc
     * @return
     */
    private static Set<String> extractThreadLinks(Document forumDoc) {
        Set<String> out = new LinkedHashSet<>();
        if (forumDoc == null) return out;

        Elements anchors = forumDoc.select("a[href]");
        // 调试输出（可注释）
        // System.out.println("链接文字内容是：" + anchors.text());

        for (Element a : anchors) {
            String href = a.attr("href").trim();
            if (href.isEmpty()) continue;

            // 典型 Discuz 网址链接：thread-<id>-1-1.html
            if (href.startsWith("thread-") && href.endsWith(".html") && !href.contains("page")) {
                String abs = a.attr("abs:href");
                if (abs == null || abs.isEmpty()) {
                    abs = makeAbsolute("https://sis001.com/forum/", href);
                }
                out.add(abs);
            } else {
                if (href.contains("thread-") && href.endsWith(".html")) {
                    String abs = a.attr("abs:href");
                    if (abs == null || abs.isEmpty()) abs = href;
                    out.add(abs);
                }
            }
        }
        return out;
    }

    /**
     * 处理帖子页：提取图片链接并下载
     * @param postUrl
     */
    private static void processPost(String postUrl) {
        try {
            Document postDoc = fetchDocumentWithJsoup(postUrl);
            if (postDoc == null) {
                System.err.println("无法解析帖子: " + postUrl);
                return;
            }

            // —— 帖子页也做 CF 挑战检测 —— //
            if (isCloudflareChallenge(postDoc)) {
                System.err.println("帖子页出现 Cloudflare 挑战，跳过：" + postUrl + "（请更新 COOKIE / 确保同一出口IP / UA 一致）");
                return;
            }

            Set<String> imgUrls = extractImageUrls(postDoc);
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
                downloadWithChecks(imgUrl, savePath.toString());
                sleepMillis(300 + new Random().nextInt(300)); // 小延时
            }
        } catch (Exception e) {
            System.err.println("处理失败，下载图片链接是 " + postUrl + "   错误信息是-> " + e.getMessage());
        }
    }

    /**
     * 从含有海报网页详情中，过滤出图片的URL，并能集合的方式返回
     * @param doc
     * @return
     */
    private static Set<String> extractImageUrls(Document doc) {
        Set<String> urls = new LinkedHashSet<>();
        if (doc == null) return urls;

        Elements imgs = doc.select("div[id^=postmessage_] img, div[id^=postmessage_] a img");
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

        // 备选：页面中可能存在直接 a[href*=attachment] 的外链图片
        Elements aImgs = doc.select("a[href]");
        for (Element a : aImgs) {
            String href = a.attr("abs:href");
            if (!isEmpty(href) && (href.matches("(?i).+\\.(jpg|jpeg|png|gif|webp)$") || href.contains("attachment") || href.contains("image"))) {
                urls.add(href);
            }
        }

        return urls;
    }

    /**
     * 下载时校验 Content-Type 为 image/*，含重试
     * @param imgUrl
     * @param savePath
     */
    private static void downloadWithChecks(String imgUrl, String savePath) {
        File f = new File(savePath);
        if (f.exists()) {
            System.out.println("    已存在，跳过，存放路径为: " + savePath);
            return;
        }

        for (int attempt = 1; attempt <= IMG_DOWNLOAD_RETRIES; attempt++) {
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
                conn.setRequestProperty("Referer", "https://sis001.com/");
                if (!COOKIE.isEmpty()) conn.setRequestProperty("Cookie", COOKIE);

                int code = conn.getResponseCode();
                if (code >= 300 && code < 400) {
                    String loc = conn.getHeaderField("Location");
                    if (loc != null && !loc.isEmpty()) {
                        imgUrl = makeAbsolute(url.toString(), loc);
                        conn.disconnect();
                        System.out.println("    302 重定向到: " + imgUrl);
                        continue;
                    }
                }
                if (code < 200 || code >= 300) {
                    throw new IOException("HTTP 非 2xx 响应: " + code);
                }

                String ctype = conn.getContentType();
                if (ctype == null || !ctype.toLowerCase(Locale.ROOT).startsWith("image/")) {
                    throw new IOException("非图片 Content-Type: " + ctype);
                }

                try (InputStream in = conn.getInputStream();
                     OutputStream out = new FileOutputStream(savePath)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                }

                System.out.println("    下载成功: " + savePath);
                return;
            } catch (IOException e) {
                System.err.println("    下载失败 (" + attempt + "/" + IMG_DOWNLOAD_RETRIES + "): " + imgUrl + " || -> 原因是" + e.getMessage());
                sleepMillis(600L * attempt);
            } finally {
                if (conn != null) conn.disconnect();
            }
        }

        System.err.println("    多次尝试后仍失败，要下载的图片网址: " + imgUrl);
    }

    /**
     * 生成安全且尽可能唯一的文件基名：清洗标题 + 提取 thread id
     * @param doc
     * @param url
     * @return
     */
    private static String generateBaseName(Document doc, String url) {
        String title = (doc != null && doc.title() != null) ? doc.title().trim() : "";
        title = title.replaceAll("\\s+", " ").replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (title.length() > 60) title = title.substring(0, 60);

        String tid = extractThreadIdFromUrl(url);
        String base = (title.isEmpty() ? "untitled" : title) + "_" + tid;
        base = base.replaceAll("[\\p{Cntrl}]", "_");
        return base;
    }

    /**
     * 
     * @param url
     * @return
     */
    private static String extractThreadIdFromUrl(String url) {
        if (url == null) return "thread";
        int p = url.indexOf("thread-");
        if (p >= 0) {
            int q = url.indexOf(".html", p);
            if (q > p) {
                return url.substring(p, q).replaceAll("[^0-9a-zA-Z-_]", "_");
            } else {
                return url.substring(p).replaceAll("[^0-9a-zA-Z-_]", "_");
            }
        }
        return "t" + Math.abs(url.hashCode());
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * 生成文件的后缀名
     * @param u
     * @return
     */
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
        if (PROXY_USE_SOCKS) {
            return new Proxy(Proxy.Type.SOCKS, addr);
        } else {
            return new Proxy(Proxy.Type.HTTP, addr);
        }
    }

    private static String makeAbsolute(String base, String relative) {
        try {
            URL baseUrl = new URL(base);
            return new URL(baseUrl, relative).toString();
        } catch (MalformedURLException e) {
            return relative;
        }
    }

    private static void sleepMillis(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
