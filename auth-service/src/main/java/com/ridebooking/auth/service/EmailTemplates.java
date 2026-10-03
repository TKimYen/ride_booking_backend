package com.ridebooking.auth.service;

import com.ridebooking.auth.config.EmailProperties;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Nội dung email. Mỗi email có cả bản HTML và bản text thuần: nhiều khách hàng
 * đọc bằng client không render HTML, và bản text tránh phụ thuộc CSS.
 *
 * <p>Dùng bảng bố cục cũ thay vì flex/grid vì một số client email (đặc biệt Outlook)
 * bỏ qua CSS hiện đại.
 */
public final class EmailTemplates {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi"));

    private EmailTemplates() {
    }

    /** Link đầy đủ tới trang xử lý trên frontend. */
    public static String link(EmailProperties properties, String path, String token) {
        String base = properties.getFrontendUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path + "?token=" + token;
    }

    public static String verifyEmailSubject() {
        return "Xác minh địa chỉ email của bạn";
    }

    public static String verifyEmail(EmailProperties properties, String token, long ttlMinutes) {
        String url = link(properties, "/verify-email", token);
        String html = layout("Xác minh địa chỉ email",
                "<p>Chào mừng bạn đến với <b>Ride Booking</b>!</p>"
                        + "<p>Để hoàn tất đăng ký, vui lòng xác minh địa chỉ email này. "
                        + "Nếu bạn không tự mở tài khoản này thì hãy bỏ qua email.</p>"
                        + button(url, "Xác minh email")
                        + "<p style=\"color:#666;font-size:13px\">Link hết hạn sau "
                        + minutes(ttlMinutes) + ".</p>");
        String text = "Chào mừng bạn đến với Ride Booking!\n\n"
                + "Xác minh địa chỉ email bằng link sau (hết hạn sau " + minutes(ttlMinutes) + "):\n"
                + url + "\n\nNếu bạn không tự mở tài khoản này thì hãy bỏ qua email này.\n";
        return wrap(html, text);
    }

    public static String resendVerifySubject() {
        return "Mã xác minh email mới";
    }

    public static String resendVerify(EmailProperties properties, String token, long ttlMinutes) {
        String url = link(properties, "/verify-email", token);
        String html = layout("Mã xác minh email mới",
                "<p>Bạn vừa yêu cầu gửi lại mã xác minh email.</p>"
                        + button(url, "Xác minh email")
                        + "<p style=\"color:#666;font-size:13px\">Link hết hạn sau "
                        + minutes(ttlMinutes) + ". Nếu bạn không yêu cầu, hãy bỏ qua email này.</p>");
        String text = "Bạn vừa yêu cầu gửi lại mã xác minh email (hết hạn sau "
                + minutes(ttlMinutes) + "):\n" + url + "\n";
        return wrap(html, text);
    }

    public static String forgotPasswordSubject() {
        return "Đặt lại mật khẩu Ride Booking";
    }

    public static String forgotPassword(EmailProperties properties, String token, long ttlMinutes) {
        String url = link(properties, "/reset-password", token);
        String html = layout("Đặt lại mật khẩu",
                "<p>Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản này.</p>"
                        + button(url, "Đặt mật khẩu mới")
                        + "<p style=\"color:#666;font-size:13px\">Link hết hạn sau "
                        + minutes(ttlMinutes) + " và chỉ dùng được một lần. "
                        + "Sau khi đổi mật khẩu, mọi phiên đăng nhập khác sẽ bị đăng xuất.</p>"
                        + "<p style=\"color:#b91c1c;font-size:13px\">Nếu bạn KHÔNG yêu cầu đặt lại mật khẩu, "
                        + "hãy bỏ qua email này. Nếu bạn nghi ngờ tài khoản bị truy cập trái phép, "
                        + "vui lòng báo cho chúng tôi.</p>");
        String text = "Chúng tôi nhận được yêu cầu đặt lại mật khẩu (hết hạn sau "
                + minutes(ttlMinutes) + ", dùng một lần):\n" + url
                + "\n\nNếu bạn không yêu cầu điều này, hãy bỏ qua email.\n";
        return wrap(html, text);
    }

    public static String changedPasswordSubject() {
        return "Mật khẩu của bạn đã được thay đổi";
    }

    public static String changedPassword(String changedAt) {
        String html = layout("Mật khẩu đã được thay đổi",
                "<p>Mật khẩu tài khoản của bạn vừa được thay đổi lúc "
                        + "<b>" + escape(changedAt) + "</b> và mọi phiên đăng nhập khác đã bị đăng xuất.</p>"
                        + "<p>Nếu không phải bạn thực hiện, hãy đổi mật khẩu ngay và liên hệ chúng tôi.</p>");
        String text = "Mật khẩu của bạn vừa được thay đổi lúc " + changedAt
                + " và mọi phiên đăng nhập khác đã bị đăng xuất.\n"
                + "Nếu không phải bạn, hãy đổi mật khẩu ngay.\n";
        return wrap(html, text);
    }

    public static String emailChangedSubject() {
        return "Địa chỉ email của bạn đã được thay đổi";
    }

    public static String emailChanged(String oldEmail, String newEmail) {
        String html = layout("Email đã được thay đổi",
                "<p>Địa chỉ email của tài khoản đã đổi:</p>"
                        + "<p style=\"background:#f5f5f5;padding:12px;border-radius:6px\">"
                        + escape(oldEmail) + " <br>→ <b>" + escape(newEmail) + "</b></p>"
                        + "<p style=\"color:#b91c1c\">Nếu bạn không thực hiện thay đổi này, "
                        + "hãy liên hệ chúng tôi ngay — có thể ai đó đã chiếm tài khoản của bạn.</p>");
        String text = "Địa chỉ email đã đổi từ " + oldEmail + " sang " + newEmail
                + ".\nNếu không phải bạn, hãy liên hệ chúng tôi ngay.\n";
        return wrap(html, text);
    }

    public static String emailChangeWarningSubject() {
        return "Cảnh báo: ai đó đang cố đổi email của bạn";
    }

    public static String emailChangeWarning(String oldEmail, String newEmail) {
        String html = layout("Cảnh báo bảo mật",
                "<p style=\"color:#b91c1c;font-size:16px\"><b>Có ai đó đang cố đổi email của bạn.</b></p>"
                        + "<p>Yêu cầu đổi email từ <b>" + escape(oldEmail) + "</b> sang <b>"
                        + escape(newEmail) + "</b> chưa được hoàn tất.</p>"
                        + "<p>Chúng tôi gửi email này cho bạn để chắc chắn đây không phải hành vi của bạn. "
                        + "Bạn KHÔNG cần làm gì cả — thay đổi chỉ có hiệu lực khi được xác nhận ở email mới.</p>"
                        + "<p style=\"color:#b91c1c;font-size:13px\">Nếu không phải bạn, "
                        + "hãy đổi mật khẩu ngay lập tức.</p>");
        String text = "CẢNH BÁO: có ai đó đang cố đổi email của bạn từ " + oldEmail
                + " sang " + newEmail + ". Thay đổi chưa có hiệu lực.\n"
                + "Nếu không phải bạn, hãy đổi mật khẩu ngay.\n";
        return wrap(html, text);
    }

    public static String emailChangeConfirmSubject() {
        return "Xác nhận địa chỉ email mới";
    }

    public static String emailChangeConfirm(EmailProperties properties, String newEmail,
                                            String token, long ttlMinutes) {
        String url = link(properties, "/confirm-email-change", token);
        String html = layout("Xác nhận email mới",
                "<p>Để đổi email của bạn sang <b>" + escape(newEmail) + "</b>, "
                        + "vui lòng xác nhận tại email mới.</p>"
                        + button(url, "Xác nhận đổi email")
                        + "<p style=\"color:#666;font-size:13px\">Link hết hạn sau "
                        + minutes(ttlMinutes) + ".</p>");
        String text = "Xác nhận đổi email sang " + newEmail + " (hết hạn sau "
                + minutes(ttlMinutes) + "):\n" + url + "\n";
        return wrap(html, text);
    }


    private static String button(String url, String label) {
        return "<p style=\"margin:24px 0\">"
                + "<a href=\"" + url + "\" "
                + "style=\"background:#16a34a;color:#fff;padding:12px 24px;border-radius:6px;"
                + "text-decoration:none;font-weight:bold;display:inline-block\">"
                + label + "</a></p>"
                + "<p style=\"font-size:12px;color:#888;word-break:break-all\">"
                + "Nếu nút không hoạt động, hãy mở link: " + escape(url) + "</p>";
    }

    private static String layout(String title, String content) {
        return "<!DOCTYPE html><html lang=\"vi\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>" + escape(title) + "</title></head>"
                + "<body style=\"margin:0;padding:24px;background:#f4f4f5;"
                + "font-family:Segoe UI,Roboto,Helvetica,Arial,sans-serif;color:#18181b\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">"
                + "<tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"560\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"max-width:560px;background:#fff;border-radius:8px;padding:32px\">"
                + "<tr><td>"
                + "<h1 style=\"font-size:20px;margin:0 0 16px\">" + escape(title) + "</h1>"
                + content
                + "<hr style=\"border:none;border-top:1px solid #eee;margin:24px 0\">"
                + "<p style=\"font-size:12px;color:#888\">Email này được gửi tự động, "
                + "vui lòng không trả lời.</p>"
                + "</td></tr></table>"
                + "<p style=\"font-size:12px;color:#888;margin-top:16px\">© Ride Booking</p>"
                + "</td></tr></table></body></html>";
    }

    private static String wrap(String html, String text) {
        return html + "\n<!--TEXT-->\n" + text;
    }

    /** Tách nội dung thành cặp (html, text) để {@link MailService} dùng. */
    public static String[] split(String combined) {
        int marker = combined.indexOf("\n<!--TEXT-->\n");
        if (marker < 0) {
            return new String[]{combined, combined.replaceAll("<[^>]+>", "")};
        }
        return new String[]{
                combined.substring(0, marker),
                combined.substring(marker + "\n<!--TEXT-->\n".length())
        };
    }

    private static String minutes(long ttlMinutes) {
        if (ttlMinutes >= 1440) {
            long days = ttlMinutes / 1440;
            return days + (days == 1 ? " ngày" : " ngày");
        }
        if (ttlMinutes >= 60) {
            long hours = ttlMinutes / 60;
            return hours + (hours == 1 ? " giờ" : " giờ");
        }
        return ttlMinutes + " phút";
    }

    /** Chống chèn mã độc qua tên/email do người dùng nhập vào nội dung email. */
    public static String escape(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public static String formatTime(LocalDateTime time) {
        return time.format(FORMAT);
    }
}
