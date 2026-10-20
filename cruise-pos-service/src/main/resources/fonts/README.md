# Fonts

## NotoSans-Regular.ttf

- Nguồn: https://github.com/notofonts/notofonts.github.io (fonts/NotoSans/hinted/ttf/NotoSans-Regular.ttf)
- Giấy phép: SIL Open Font License 1.1 (cho phép nhúng và phân phối lại kèm phần mềm)
- Mục đích: nhúng vào hoá đơn PDF để hiển thị đúng tiếng Việt có dấu.

Khi thay font, cần chạy lại test `InvoicePdfServiceTest` để đảm bảo:
- hoá đơn vẫn tất định (cùng SHA-256 cho cùng settlement),
- text extraction vẫn đọc được tiếng Việt có dấu.
