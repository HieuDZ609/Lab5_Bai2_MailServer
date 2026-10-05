# Lab 5 — Bài 2: Mail Server & Mail Client trên UDP

Bài tập lập trình UDP socket: xây dựng **Mail Server** và **Mail Client** bằng Java
hỗ trợ 3 chức năng — tạo tài khoản, gửi email, đăng nhập và xem danh sách email.

> Chi tiết lý thuyết nền tảng (mô hình mạng, tầng transport, hệ thống email, RFC 5322/1939/4954…)
> nằm trong **[`LY_THUYET.md`](LY_THUYET.md)**.

---

## 1. Yêu cầu đề bài

| # | Yêu cầu | Cách hiện thực |
|---|---|---|
| 1 | Tạo tài khoản mới, sinh file `new_email.txt` chào mừng | `REGISTER` → `data/<user>/new_email.txt` |
| 2 | Gửi email, lưu thành **mỗi email một file** | `SEND` → `mail_XXXX.txt` trong thư mục người nhận |
| 3 | Đăng nhập và trả về **danh sách tên file** email | `LOGIN` → `mail_0001.txt~mail_0002.txt~new_email.txt` |

**Phần mở rộng (KHÔNG phải yêu cầu đề bài)** — do sinh viên tự thêm để demo:

| Lệnh | Việc làm | Vì sao thêm |
|---|---|---|
| `LOGOUT` | Xác nhận thoát phiên, phía client xoá tài khoản + danh sách thư | Trả GUI về trạng thái trước khi đăng nhập |
| `LIST` | Trả về danh sách tên file thư của một tài khoản | Client **poll 1 giây/lần** nên thư mới hiện realtime, không cần bấm tải lại |
| `FETCH` | Trả về **nội dung** của một file thư | Để xem được thư; `LOGIN` chỉ trả *tên file* |

> ⚠️ **`LIST` và `FETCH` không yêu cầu mật khẩu** (xem [§8.4](#84-cảnh-báo-bảo-mật-của-phần-mở-rộng)).
> Đây là đánh đổi được chấp nhận có chủ ý để giữ protocol gọn; bài thực tế phải xác thực.

---

## 2. Yêu cầu môi trường

- **JDK 17 trở lên** (dùng `List.toList()`, `switch` expression)
- Không cần thư viện ngoài — chỉ dùng JDK standard library

Kiểm tra phiên bản:

```bash
java -version
javac -version
```

> Máy tác giả dùng JDK tại `/mnt/Nigga/Code/jdk17` vì `javac` không có sẵn trong `PATH`.
> Nếu máy bạn đã cài JDK bình thường thì dùng `javac` / `java` như bình thường.

---

## 3. Biên dịch

```bash
cd Lab5_Bai2_MailServer

# Nếu javac có sẵn trong PATH:
javac -encoding UTF-8 -d build src/*.java

# Nếu không có javac trong PATH, dùng đường dẫn đầy đủ:
/mnt/Nigga/Code/jdk17/bin/javac -encoding UTF-8 -d build src/*.java
```

`-encoding UTF-8` là **bắt buộc**, vì code chứa chuỗi tiếng Việt không dấu và có comment tiếng Việt.

---

## 4. Chạy chương trình

Cả hai chương trình đều là **giao diện đồ hoạ Swing**. Không còn bản console.

### 4.1. Mở terminal 1 — Mail Server

```bash
cd Lab5_Bai2_MailServer
java -Dfile.encoding=UTF-8 -cp build MailServerFrame
```

Cửa sổ server gồm:

| Vùng | Nội dung |
|---|---|
| Thanh tiêu đề | Tên chương trình + đèn trạng thái (đang chạy / đã dừng) |
| Thẻ **Cấu hình** | Ô `Cổng UDP` (mặc định `2346`), ô `Thư mục dữ liệu` (mặc định `data/` trong thư mục dự án), nút `Chọn…` |
| Nút | `Bắt đầu` — mở socket và bắt đầu lắng nghe · `Dừng` — đóng socket |
| Thẻ **Nhật ký hoạt động** | Mỗi request/response, giữ 500 dòng gần nhất |
| Chân trang | Nút `Xoá nhật ký` |

Đóng cửa sổ = dừng server.

### 4.2. Mở terminal 2 — Mail Client

```bash
cd Lab5_Bai2_MailServer
java -Dfile.encoding=UTF-8 -cp build MailClientFrame
```

Cửa sổ client gồm:

| Vùng | Nội dung |
|---|---|
| Cột trái — **Kết nối máy chủ** | Ô `Máy chủ` (mặc định `localhost`), ô `Cổng UDP` (mặc định `2346`)<br>Hàng 1: `Kết nối` · `Ngắt` — Hàng 2: `Đăng xuất` (**chỉ hiện sau khi đăng nhập**) |
| Cột trái — **Hộp thư** | Danh sách tệp thư của tài khoản đang đăng nhập (**ẩn cho tới khi đăng nhập**). Thư mới có dấu `●` màu nhấn mạnh |
| Cột phải — 4 tab | `Đăng ký` · `Đăng nhập` · `Gửi thư` · `Đọc thư` |
| Thanh tiêu đề | Đèn trạng thái + tài khoản/địa chỉ đang dùng |

**Giao diện chỉ mở những gì dùng được.** Trước khi đăng nhập chỉ có 2 tab `Đăng ký` / `Đăng nhập`;
thẻ `Hộp thư`, tab `Gửi thư`, tab `Đọc thư` và nút `Đăng xuất` đều **ẩn**. Sau khi đăng nhập thì
cả 4 tab và thẻ `Hộp thư` mới hiện, đồng thời tự bật vòng poll 1 giây/lần.

`-Dfile.encoding=UTF-8` giúp Swing hiển thị đúng tiếng Việt có dấu trên cả Windows lẫn Linux.

> **Phải bấm `Kết nối` trước.** `Kết nối` chỉ mở socket — chưa gửi request nào. Các thao tác
> `Đăng ký` / `Đăng nhập` / `Gửi thư` mới thực sự trao đổi với máy chủ. Thông báo lỗi luôn hiện
> đúng ở tab đang bấm, nên không lẫn sang tab khác.

---

## 5. Hướng dẫn sử dụng Client

| Tab | Nhập gì | Kết quả |
|---|---|---|
| **Đăng ký** | Tên tài khoản (3–32 ký tự: `a-z`, `A-Z`, `0-9`, `_`) + mật khẩu | `200` tạo tài khoản kèm thư chào mừng; `409` nếu tên đã tồn tại |
| **Đăng nhập** | Tên tài khoản + mật khẩu | `200` kèm danh sách tệp thư, điền sẵn ô *Người gửi*; `401` nếu sai mật khẩu |
| **Gửi thư** | Người nhận, tiêu đề, nội dung (nhiều dòng) | `200` kèm tên tệp vừa lưu; `404` nếu người nhận không tồn tại |
| **Đọc thư** | Không cần nhập gì — bấm một tệp trong `Hộp thư` | Tự gọi `FETCH`, hiện header (`Từ` / `Đến` / `Tiêu đề` / `Ngày`) + nội dung; `404` nếu tệp không còn |

Vùng kết quả dưới mỗi tab hiện mã trạng thái kèm thông điệp của máy chủ:
xanh lá là thành công, đỏ là lỗi.

Trong tab **Gửi thư**, ô *Nội dung* có bộ đếm ký tự (giới hạn 8000) và chuyển sang đỏ khi vượt hạn.

### Nhận thư realtime

Sau khi đăng nhập, client **tự poll `LIST` mỗi 1 giây** ở một luồng nền. Người dùng không cần
bấm làm mới: thư mới do máy khủ gửi tới sẽ tự xuất hiện trong `Hộp thư` (có dấu `●`) trong
khoảng 1–2 giây. Vòng poll **dừng ngay** khi bấm `Đăng xuất`, `Ngắt` hoặc đóng cửa sổ.

### Đăng xuất

Bấm `Đăng xuất` → gửi `LOGOUT`, xoá tài khoản và danh sách thư khỏi bộ nhớ, dừng poll,
ẩn lại thẻ `Hộp thư` + tab `Gửi thư`/`Đọc thư` + nút `Đăng xuất`, và quay về tab `Đăng nhập`.
Kết nối UDP **vẫn giữ**, nên đăng nhập lại không cần bấm `Kết nối`.

---

## 6. Ví dụ demo hoàn chỉnh

Kịch bản: Alice tạo tài khoản, Bob tạo tài khoản, hai người gửi thư cho nhau, Bob đăng nhập.

Nhật ký trong cửa sổ **Mail Server**:

```
[00:13:36] [1] ← 127.0.0.1:40838  REGISTER|alice|123456
[00:13:36] [1] → 127.0.0.1:40838  200|Account 'alice' created
[00:13:36] [2] ← 127.0.0.1:40838  REGISTER|bob|abc123
[00:13:36] [2] → 127.0.0.1:40838  200|Account 'bob' created
[00:13:45] [6] ← 127.0.0.1:42648  SEND|alice|bob|Bai tap Lab 5|Xin chao Bob!<BR>Day la email dau tien Alice gui.
[00:13:45] [6] → 127.0.0.1:42648  200|Delivered to 'bob' as file mail_0001.txt
[00:13:45] [7] ← 127.0.0.1:42648  SEND|bob|alice|Tra loi|Cam on Alice!
[00:13:45] [7] → 127.0.0.1:42648  200|Delivered to 'alice' as file mail_0001.txt
[00:13:45] [9] ← 127.0.0.1:42648  LOGIN|bob|abc123
[00:13:45] [9] → 127.0.0.1:42648  200|mail_0001.txt~new_email.txt
[00:13:45] [10] ← 127.0.0.1:42648 LIST|bob
[00:13:45] [10] → 127.0.0.1:42648 200|mail_0001.txt~new_email.txt
[00:13:45] [11] ← 127.0.0.1:42648 FETCH|bob|mail_0001.txt
[00:13:45] [11] → 127.0.0.1:42648 200|From: alice@mailserver.local<BR>...
```

> Hai cặp `[10]` và `[11]` ở trên là **poll nền và lúc bấm xem thư** — chúng xuất hiện
> liên tục, không phải người dùng gõ tay. Số thứ tự request tăng dần mỗi giây khi
> còn phiên đăng nhập.

Thao tác tương ứng phía client:

| Bước | Tab | Thao tác | Kết quả hiện trên màn hình |
|---|---|---|---|
| 1 | Đăng ký | `alice` / `123456` → **Tạo tài khoản** | `200 · Account 'alice' created` |
| 2 | Đăng ký | `bob` / `abc123` → **Tạo tài khoản** | `200 · Account 'bob' created` |
| 3 | Gửi thư | `alice` → `bob`, *Bài tập Lab 5* → **Gửi thư đi** | `200 · Delivered to 'bob' as file mail_0001.txt` |
| 4 | Gửi thư | `bob` → `alice`, *Trả lời* → **Gửi thư đi** | `200 · Delivered to 'alice' as file mail_0001.txt` |
| 5 | Đăng nhập | `bob` / `abc123` → **Vào hộp thư** | `200 · mail_0001.txt~new_email.txt`, hộp thư hiện 2 tệp |

**Kịch bản realtime** (cần **hai cửa sổ client**):

| Bước | Cửa sổ | Thao tác | Kết quả |
|---|---|---|---|
| 6 | Cửa sổ B | đăng nhập `bob` → ở tab `Gửi thư`, để nguyên | Hộp thư B hiện 2 tệp |
| 7 | Cửa sổ A | đăng nhập `alice`, gửi `Chào Bob` cho `bob` | `200 · Delivered to 'bob' as file mail_0002.txt` |
| 8 | Cửa sổ B | **không làm gì cả**, chờ 1–2 giây | Tệp `mail_0002.txt` tự xuất hiện với dấu `●` |
| 9 | Cửa sổ B | bấm `mail_0002.txt` | Sang tab `Đọc thư`, hiện đúng header và nội dung |
| 10 | Cửa sổ B | bấm `Đăng xuất` | Thẻ `Hộp thư` và tab `Gửi thư`/`Đọc thư` biến mất, poll dừng |

---

## 7. Cấu trúc thư mục dữ liệu

Sau khi đăng ký `alice`, `bob` và gửi 3 email:

```
data/
├── accounts.dat                 # lưu hash SHA-256 mật khẩu
├── alice/
│   ├── new_email.txt            # file chào mừng (tạo lúc REGISTER)
│   └── mail_0001.txt            # email bob gửi cho alice
└── bob/
    ├── new_email.txt
    ├── mail_0001.txt
    └── mail_0002.txt
```

Nội dung một file email (đúng chuẩn RFC 5322):

```
From: alice@mailserver.local
To: bob@mailserver.local
Subject: Bai tap Lab 5
Date: Sun, 04 Oct 2026 00:13:45 +0700
Message-ID: <1791047625.b415d@mailserver.local>
MIME-Version: 1.0
Content-Type: text/plain; charset="UTF-8"

Xin chao Bob! Day la email dau tien Alice gui.
```

> Thư mục `data/` nằm **trong thư mục dự án**, không nằm cạnh các file `.java` —
> `MailServer.DEFAULT_DATA_DIR` trỏ tới đường dẫn tuyệt đối đó và `MailServerFrame`
> lấy từ hằng số này.
>
> `accounts.dat` lưu **hash SHA-256**, không lưu mật khẩu rõ.
> Đây là mức tối thiểu cho bài tập — bài thực tế cần PBKDF2/bcrypt/Argon2 kèm salt
> (xem §3.4.3 trong `LY_THUYET.md`).

Dữ liệu được lưu trên đĩa nên **giữ nguyên sau khi restart server**.

---

## 8. Protocol

### 8.1. Định dạng

```
REQUEST :  <OP>|<arg1>|<arg2>|...|<argN>\r\n
RESPONSE:  <STATUS>|<message>\r\n
```

| Ký hiệu | Ý nghĩa |
|---|---|
| `|` | Dấu phân tách **giữa các trường** |
| `~` | Dấu phân tách **giữa các phần tử** trong message (danh sách file) |
| `\|` | Ký tự `|` nằm trong dữ liệu (escape bằng `\` trước) |
| `<BR>` | Ký tự xuống dòng `\n` |
| `\r\n` | Kết thúc request |

> UDP đã tự bảo toàn ranh giới datagram, nên `\r\n` **không bắt buộc về mặt kỹ thuật**.
> Ta vẫn thêm để đọc dòng bằng `BufferedReader`, log dễ đọc, và tương thích TCP nếu cần nâng cấp.

### 8.2. Bảng lệnh

| OP | Request | Response thành công | Response lỗi |
|---|---|---|---|
| `REGISTER` | `REGISTER\|<user>\|<pass>` | `200\|Account <user> created` | `400\|…` sai định dạng<br>`409\|Account already exists`<br>`500\|Server error` |
| `SEND` | `SEND\|<from>\|<to>\|<subject>\|<body>` | `200\|Delivered to '<to>' as file mail_XXXX.txt` | `400\|…`<br>`404\|Recipient not found` |
| `LOGIN` | `LOGIN\|<user>\|<pass>` | `200\|<f1>~<f2>~…~<fn>` | `400\|…`<br>`401\|Invalid password`<br>`404\|Account not found` |
| `LOGOUT` | `LOGOUT` | `200\|Goodbye` | — |
| `LIST` *(mở rộng)* | `LIST\|<user>` | `200\|<f1>~<f2>~…~<fn>` | `400\|…`<br>`404\|Account not found` |
| `FETCH` *(mở rộng)* | `FETCH\|<user>\|<file>` | `200\|<nội dung thư đầy đủ>` | `400\|…`<br>`404\|File not found` |

### 8.3. Bảng mã trả lời

| Mã | Ý nghĩa | Khi nào dùng |
|---|---|---|
| `200` | OK | Thành công |
| `400` | Bad Request | Sai cú pháp, tên tài khoản không hợp lệ |
| `401` | Unauthorized | Sai mật khẩu |
| `404` | Not Found | Không tìm thấy tài khoản |
| `409` | Conflict | Tài khoản đã tồn tại |
| `500` | Server Error | Lỗi hệ thống (không ghi được file…) |

### 8.4. Cảnh báo bảo mật của phần mở rộng

Ba điểm phải nói thẳng khi vấn đáp, vì chúng là **hạn chế thật** của đồ án:

1. **`LIST` và `FETCH` không có mật khẩu.** Ai gõ `FETCH|alice|new_email.txt` cũng đọc được thư
   của `alice`. Nguyên nhân: giữ đúng nguyên tắc *"cả ba lệnh bắt buộc của đề bài đều xác thực
   bằng mật khẩu"* và thêm `LOGOUT` cũng vậy, nên tính nhất quán là **không** làm nửa vời —
   thà không xác thực còn hơn có vẻ an toàn. Nếu bắt buộc phải xác thực thì phải đổi protocol
   sang `FETCH\|user\|pass\|file` (phá vỡ tính tương thích ngược với `LOGIN`).
2. **`SEND` cũng không mang phiên.** `SEND|from|to|subject|body` không có mật khẩu, và server
   **không lưu phiên**. Vì vậy `LOGOUT` chỉ là xác nhận ở tầng protocol — phần dọn dẹp thật
   sự diễn ra ở phía client (xoá `currentUser` + danh sách thư, dừng poll). Không ai gọi
   `LOGOUT` thì client vẫn coi là đã đăng xuất; nhưng nếu vẫn giữ `fromField` thì về lý thuyết
   có thể gửi tiếp. Đây là hệ quả tất yếu của protocol không có session id, không phải lỗi
   triển khai.
3. **`accounts.dat` dùng SHA-256 không salt.** Yếu hơn hẳn PBKDF2/bcrypt/Argon2 — xem §12.

Muốn làm đúng chuẩn thì cần: `SEND` mang mật khẩu hoặc session token, `LIST`/`FETCH` mang
mật khẩu, `LOGIN` trả về token có hạn, và `accounts.dat` đổi sang hàm băm chậm có salt.

---

## 9. Kiểm thử

| Tình huống | Kết quả mong đợi |
|---|---|
| Đăng ký tài khoản mới | `200`, tạo thư mục + `new_email.txt` |
| Đăng ký lại cùng tên | `409`, không ghi đè mail cũ |
| Tên tài khoản quá ngắn (`ab`) | `400` |
| Tên chứa ký tự lạ (`my user`) | `400` |
| Path traversal (`../evil`, `..\evil`, `alice/../../x`) | `400`, **không tạo file ngoài thư mục dữ liệu** |
| Gửi email cho người không tồn tại | `404` |
| Đăng nhập sai mật khẩu | `401` |
| Đăng nhập đúng | `200` + danh sách file |
| Body chứa `<BR>` | Tách thành nhiều dòng thật trong file |
| Body chứa `\|` | Ghi đúng ký tự `\|` vào file |
| Gửi cho người không có trong danh sách client | `400` |
| Không có server / server chết | Client báo `[TIMEOUT]` sau 3 giây, không treo |
| **60 client gửi email đồng thời** | **60/60 thành công**, không trùng tên file, không ghi đè |
| **15 client đăng ký cùng 1 tên** | Đúng 1 `200`, 14 `409`, `accounts.dat` không bị trùng dòng |
| **20 client đăng nhập đồng thời** | 20/20 `200`, kết quả giống hệt nhau |
| `LIST` tài khoản không tồn tại | `404` |
| `FETCH` tệp không tồn tại | `404` |
| `FETCH` có `..` hoặc `/` trong tên tệp | `400`, **không đọc được file ngoài thư mục thư** |
| Nội dung thư có `<BR>` / `\|` | `FETCH` trả về **xuống dòng thật**, `\|` giữ nguyên |
| Client B đang đăng nhập, client A gửi thư cho B | Tệp mới tự xuất hiện ở B trong 1–2 giây, không cần bấm làm mới |
| Bấm tệp trong `Hộp thư` | Sang tab `Đọc thư`, đúng `Từ`/`Đến`/`Tiêu đề`/`Ngày`/nội dung |
| `Đăng xuất` | Ẩn `Hộp thư` + `Gửi thư` + `Đọc thư`, dừng poll, quay về tab `Đăng nhập` |
| Bấm `Gửi thư` khi chưa đăng nhập | Báo lỗi **ở tab `Gửi thư`**, không lẫn sang tab `Đăng ký` |
| Tài khoản có thư mục nhưng mất dòng hash trong `accounts.dat` | `401` — không bỏ qua xác thực |
| Restart server | Tài khoản và email giữ nguyên |

### Kiểm thử tự động trên tầng GUI

Bộ kiểm thử nằm trong **`test/`**. Chạy hết bằng một lệnh:

```bash
./test/run.sh
```

Script tự biên dịch sạch `src/` vào `build/`, biên dịch `test/` vào `build-test/`, rồi chạy
từng công cụ và báo cáo. Riêng từng công cụ thì chạy tay:

```bash
java -Dfile.encoding=UTF-8 -cp build:build-test E2E        # 91 check
java -Dfile.encoding=UTF-8 -cp build:build-test GeoCheck MailClientFrame 1180 740 login
```

`E2E` chạy chính code của `MailClientFrame` (bấm nút thật qua
`FlatButton.doClick()` → `SwingWorker` → `MailClient` → UDP → `MailServer`) và kiểm tra
phản hồi hiện trên màn hình, thay vì chỉ gọi hàm lõi. Server được mở trong cùng JVM nên
không cần terminal riêng.

Kết quả thu được: **91/91 PASS**, chia làm 10 nhóm (A–K):

| Nhóm | Nội dung |
|---|---|
| A | `REGISTER` / `LOGIN`: trùng tên, sai định dạng, sai mật khẩu, không tồn tại |
| B | `LIST`: danh sách file, tài khoản sai, tên sai định dạng |
| C | `FETCH`: header đầy đủ, file thiếu, **path traversal**, tài khoản thiếu |
| D | `SEND` rồi `LIST` thấy file mới; `FETCH` giữ đúng xuống dòng và dấu escape |
| E | **Realtime**: client B thấy thư client A gửi, đo thời gian thực tế |
| F | Tài khoản thẻ mục nhưng mất dòng hash → `401`; không đọc được thư của tài khoản ngoài danh sách |
| G | `LOGOUT`: xác nhận `200`, xoá phiên, xoá danh sách thư |
| H | **Cảm giác GUI**: 4/2 tab theo trạng thái, ẩn/hiện thẻ hộp thư, bấm thư ra nội dung, đăng xuất |
| I | Server vẫn phản hồi sau khi client gửi lệnh lỗi |
| K | Người gửi xem được thư mình vừa gửi; thư đến giữa lúc đang đọc không làm mất nội dung đang xem |

> `build/` là sản phẩm, `build-test/` chỉ là class của bộ kiểm thử — không cần
> phân phối đi kèm.

Ba điểm dễ sai đã được xử lý trong bộ test và nên biết khi đọc lại code test:

1. `isLoggedIn()` được gán ở **luồng nền**, *trước khi* `done()` cập nhật giao diện trên
   EDT. Nếu chờ `isLoggedIn()` rồi kiểm tra UI thì test chạy ổn đúng một lần rồi báo fail ở
   các lần sau — phải chờ bằng **chính trạng thái UI**.
2. `isVisible()` phải hỏi **trên EDT** (`invokeAndWait`), đọc từ thread khác cho kết quả sai.
3. `CardLayout.show(container, name)` lưu key ở *constraints*, không phải `setName()` — đọc
   tab hiện tại bằng cách dò `isVisible()` của các card sẽ ra `-1`. Vì vậy
   `MailClientFrame` có sẵn field `currentTab` được `selectTab()` cập nhật.

Ngoài ra, một `JFrame` đang hiển thị giữ AWT event thread nên **JVM sẽ không tự dừng** nếu
công cụ ném exception. `InkCheck`/`GeoCheck` gọi `System.exit` trong `finally` để không bị treo
khi có lỗi.

### Kiểm tra hình học và chữ trên màn hình

Hai công cụ bổ sung, cùng nguyên tắc: **kích thước component không chứng minh được chữ
đã hiện**. Một nút có thể đúng kích thước mà chữ vẫn không vẽ ra (đúng lỗi đã gặp ở
`FlatButton` khi nền bị tô sau khi vẽ chữ).

| Công cụ | Cách kiểm |
|---|---|
| `InkCheck` | Vẽ riêng từng component vào ảnh, đếm pixel khác nền ở **phần nội** (bỏ 3px sát viền). 0 pixel ⇒ chữ không hiện |
| `GeoCheck` | So `preferredSize` với kích thước thật để phát hiện chữ bị cắt, và quét cặp component xem có chồng lấn không |

Cả hai chạy được ở chế độ **trước** và **sau** đăng nhập (thêm đối số `login`) — sau đăng nhập
mới kiểm được các tab vốn bị ẩn. Kết quả: `InkCheck` xanh ở 14/14 component của server và
43–53 component của client; `GeoCheck` xanh ở `1000x640`, `1100x700`, `1180x740`, `1280x820`,
cả trước và sau đăng nhập.

> `GeoCheck` **không** bắt được lỗi `FlowLayout` làm component xuống dòng — vì chúng không
> chồng lấn, chỉ nằm khác hàng. Loại lỗi đó phải nhìn tọa độ `y` của từng nút; đó là lý do
> 3 nút trong thẻ *Kết nối* được bố trí thành hai hàng cố ý (xem `LY_THUYET.md` §3.7.4).

### Script test nhanh bằng `python3` (tùy chọn, chỉ kiểm tầng protocol)

```python
import socket, threading

def rq(raw):
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM); s.settimeout(3)
    s.sendto(raw.encode("utf-8"), ("127.0.0.1", 2346))
    try:
        d, _ = s.recvfrom(70000); return d.decode("utf-8").strip()
    except socket.timeout: return "TIMEOUT"
    finally: s.close()

print(rq("REGISTER|alice|123456\r\n"))          # 200
print(rq("REGISTER|alice|123456\r\n"))          # 409
print(rq("SEND|alice|bob|Hello|Dong 1<BR>Dong 2\r\n"))  # 200, tạo mail_0001.txt
print(rq("LOGIN|bob|abc123\r\n"))               # 200, trả về danh sách file
print(rq("LOGIN|bob|sai\r\n"))                 # 401
print(rq("LIST|bob\r\n"))                       # 200, danh sách file (mở rộng)
print(rq("FETCH|bob|mail_0001.txt\r\n"))        # 200, nội dung thư (mở rộng)
print(rq("FETCH|bob|../../accounts.dat\r\n"))   # 400, chặn path traversal
print(rq("LOGOUT\r\n"))                         # 200
```

> Script này gọi thẳng UDP nên **không** chứng minh được giao diện có đúng không. Phần GUI
> phải dùng `E2E` / `InkCheck` / `GeoCheck` ở trên.

---

## 10. Giải thích code

| File | Trách nhiệm |
|---|---|
| `src/Protocol.java` | Định nghĩa hằng số, escape/unescape, tách & ghép request/response, mã trạng thái |
| `src/Mailbox.java` | Nghiệp vụ lưu trữ: tạo account, hash SHA-256, ghi/đọc file email |
| `src/MailServer.java` | UDP socket, listener thread, worker pool, điều phối request → `Mailbox`, đẩy log qua `Consumer<String>` |
| `src/MailClient.java` | Socket UDP phía client, gửi request, nhận & phân tích response |
| `src/Theme.java` | Design token: màu, font, khoảng cách, bo góc; nút phẳng tự vẽ |
| `src/MailServerFrame.java` | Cửa sổ server: cấu hình, Start/Stop, nhật ký real-time |
| `src/MailClientFrame.java` | Cửa sổ client: kết nối, hộp thư, 4 tab REGISTER / LOGIN / SEND / READ, vòng poll, đăng xuất |

### Vài điểm kỹ thuật đáng chú ý

**Server xử lý nhiều client đồng thời.** Vòng `while` chính chỉ nhận datagram rồi đẩy sang
`ExecutorService` để xử lý song song, nên một client gửi mail 500 KB không chặn các client khác.

**Mọi request phải chạy ngoài EDT.** `request()` trong `MailClient` chặn tới
`TIMEOUT_MS` (3000 ms). Gọi nó trực tiếp trên Event Dispatch Thread sẽ treo toàn bộ giao diện —
kể cả repaint, bàn phím và nút đóng cửa sổ. Vì vậy cả ba thao tác của client đều đi qua
`SwingWorker`: `doInBackground()` chạy trên luong nền, `done()` chạy lại trên EDT để cập nhật nhãn kết quả.

**`request()` phải `synchronized`.** Client giữ một `DatagramSocket` cho cả phiên. Nếu hai luồng
cùng gọi, luồng A `send()` xong thì luồng B có thể `receive()` đúng response của A — hoàn toàn lệch
mã mà không phát hiện được, vì datagram không mang định danh. GUI còn khoá giao diện trong lúc
chờ (`busy`) nên về mặt người dùng luôn chỉ có một request bay.

**Poll realtime nằm ngoài EDT, và dừng được.** `startPolling()` dùng một
`ScheduledExecutorService` với thread **daemon**, chạy `LIST` mỗi 1 giây rồi đẩy kết quả về
EDT bằng `invokeLater`. Ba điều dễ sai đều đã xử lý:

- **Dừng poll trước khi đóng client.** Nếu không, một vòng poll đang chạy sẽ thấy
  `client == null` giữa chừng và ghi trạng thái của socket đã đóng. `disconnect()` và
  đóng cửa sổ đều gọi `stopPolling()` **trước** `closeClientQuietly()`.
- **Không chồng vòng poll với thao tác của người dùng.** Cả `pollOnce()` và các handler
  đều kiểm tra `busy`; nếu đang bận thì bỏ qua vòng đó. Ngoài ra `MailClient.request()`
  `synchronized` nên không bao giờ có hai request cùng tranh một socket.
- **Thread daemon** để đóng cửa sổ không bị treo bởi executor còn chạy.

So sánh danh sách trước/sau bằng `List.equals` trước khi cập nhật model, để không dựng lại
`JList` mỗi giây. Chỉ những tên file **mới xuất hiện** mới được đánh dấu chưa đọc (`●`), và
`●` được xoá khi bấm xem thư.

**`LIST` cũng cập nhật `currentMailList` của client.** Nhờ vậy `MailClientFrame` và
`MailClient` luôn thống nhất, và E2E kiểm được bằng cách đọc thẳng trạng thái client sau khi
poll chạy.

**Log của server đẩy qua `Consumer<String>`, không `println`.** `MailServer` nhận một
`logSink`; `MailServerFrame` đưa từng dòng vào hàng đợi rồi `invokeLater` mới cập nhật `JTextArea`.
Gọi `setText()` từ luong nghe datagram sẽ phá vỡ tính an toàn của luồng Swing.

**`deliverMail()` phải `synchronized`.** Sinh tên file và ghi file phải là một khối nguyên tử.
Nếu không khóa, nhiều thread cùng tính `max + 1` sẽ sinh ra cùng tên `mail_0007.txt` và ghi đè
nhau — đây là race condition kinh điển. *(Đã test: 60 client đồng thời → 60 file khác nhau.)*

**Ghi file tạm rồi mới đổi tên.** `Files.writeString(tmp)` → `Files.move(tmp, target)` đảm bảo
client đăng nhập đúng lúc đang ghi sẽ không thấy một file nửa vời.

**`FileAlreadyExistsException` là điều kiện bình thường, không phải lỗi hệ thống.** Khi nhiều
client cùng đăng ký một tên, `createDirectory` ném exception này — code bắt riêng và trả `409`
thay vì `500`.

**Client luôn có timeout.** `setSoTimeout(3000)` đảm bảo nếu datagram reply bị mất (UDP không
đảm bảo tin cậy), chương trình báo lỗi thay vì treo vô hạn. Riêng lệnh `LIST` của vòng poll dùng
**timeout ngắn hơn (800 ms)** — poll là việc nền, không nên để người dùng phải chờ 3 giây khi
mạng chậm, và một vòng bị timeout thì bỏ qua vòng sau là xong.

**Cảnh báo nội dung phải tính theo byte, không theo ký tự.** `FETCH` giới hạn 60000 **ký tự**,
nhưng datagram chỉ chứa được 65535 **byte** UTF-8 — 60000 ký tự tiếng Việt có dấu vượt mức đó.
Nội dung do chính client gửi lên đã bị chặn ở 8000 ký tự nên không xảy ra trong luồng chuẩn;
giới hạn 60000 chỉ để phòng file bị đặt tay vào thư mục dữ liệu. Nếu nâng lên thành tính năng
chính thức thì phải đo theo byte đã encode.

**`requireClient()` phải biết đang bấm ở tab nào.** Thông báo "chưa kết nối" trước đây luôn
ghi vào nhãn của tab `Đăng ký`, nên bấm `Gửi thư` lại thấy lỗi ở tab không liên quan.
Vì vậy hàm này nhận tham số là nhãn cần báo lỗi, mỗi thao tác truyền đúng nhãn của mình.

---

## 11. Khắc phục sự cố

| Hiện tượng | Nguyên nhân & cách sửa |
|---|---|
| `[TIMEOUT] Khong phan hoi tu may chu` | Server chưa chạy, sai IP/port, hoặc firewall chặn UDP. Kiểm tra lại terminal server. |
| `error: invalid flag release 17` | `javac` trong `PATH` là bản cũ. Dùng JDK 17: `/mnt/Nigga/Code/jdk17/bin/javac` hoặc cài JDK 17+. |
| `Khong ghi duoc file email: ...` | Thiếu quyền ghi vào thư mục dữ liệu, hoặc thư mục dữ liệu không tồn tại. |
| Tiếng Việt hiển thị lỗi font | Bỏ sót `-Dfile.encoding=UTF-8`, hoặc chưa set `-encoding UTF-8` khi biên dịch. |
| `BindException: Address already in use` | Cổng 2346 đã bị chiếm. Đổi cổng trong ô *Cổng UDP* của cửa sổ server rồi bấm `Bắt đầu`. |
| Muốn reset toàn bộ dữ liệu | Xóa thư mục `data/` rồi chạy lại server. |
| Không thấy tab `Gửi thư` / `Đọc thư` / thẻ `Hộp thư` | Đúng thiết kế — chúng chỉ hiện sau khi đăng nhập thành công. |
| Bấm tài khoản khác không ra thư mới | Vòng poll dừng khi đăng xuất hoặc ngắt kết nối. Bấm `Kết nối` + `Đăng nhập` lại để bật poll. |
| Bấm nút mà không có phản hồi gì | Đang có request trước đó chưa xong. Nay đã có dòng báo *"Đang xử lý yêu cầu trước, thử lại sau."*; chờ nó xong rồi bấm lại. |
| `ArrayIndexOutOfBoundsException: arraycopy: length -2 is negative` trong `X11InputMethodBase` | **Lỗi của JDK/AWT, không phải của app** — xem mục ngay dưới. |

### Cảnh báo `X11InputMethodBase ... arraycopy: length -N is negative`

```
WARNING: Exception on Toolkit thread
java.lang.ArrayIndexOutOfBoundsException: arraycopy: length -2 is negative
    at java.desktop/sun.awt.X11InputMethodBase$IntBuffer.remove(...)
    at java.desktop/sun.awt.X11InputMethod.dispatchComposedText(...)
```

Đây là lỗi **của tầng input method của JDK 17 trên X11**, không liên quan tới code của bài:
toàn bộ stack nằm trong `sun.awt.*`, không có frame nào thuộc `src/`. Nó xảy ra khi bộ gõ tiếng
Việt (IBus) gửi sự kiện *composed text* — tức lúc đang gõ, không phải lúc bấm nút.

Cách xử lý, theo thứ tự nên thử:

1. **Chạy app bằng JRE 21** (máy này có sẵn, chỉ thiếu `javac` — vẫn biên dịch bằng JDK 17 rồi
   chạy bằng JRE 21 được):
   ```bash
   /usr/lib/jvm/java-1.21.0-openjdk-amd64/bin/java -Dfile.encoding=UTF-8 -cp build MailClientFrame
   ```
2. **Đổi bộ gõ**: chuyển từ IBus sang fcitx5, hoặc tắt hẳn IBus khi chạy app
   (`GTK_IM_MODULE= QT_IM_MODULE= XMODIFIERS= java ...`).
3. Nếu vẫn còn, cứ bỏ qua: đây là `WARNING` trên *toolkit thread*, app vẫn chạy; chỉ là
   lúc **gõ tiếng Việt** có thể bị rơi ký tự.

---

## 12. Câu hỏi thường gặp khi vấn đáp

<details>
<summary><b>UDP không có ranh giới message thì làm sao biết đã nhận đủ?</b></summary>

Tiền đề này **không đúng**. UDP *có* ranh giới message: trường `Length` trong header cho biết
datagram dài bao nhiêu, nên `receive()` luôn trả về **trọn vẹn** 1 datagram, không bao giờ bị cắt
hay gộp. Cái UDP không có là ranh giới **giữa các trường logic** bên trong datagram — vì vậy ta
tự quy ước `|` làm dấu tách trường, `\|` để escape, `<BR>` cho xuống dòng, và kiểm tra số
lượng trường sau khi parse.

*(Ngược lại mới là TCP: byte-stream có thể gộp 2 lần `write()` vào 1 lần `read()`, hoặc cắt
một message làm 2 lần `read()` — đó mới là trường hợp phải tự thiết kế framing.)*
</details>

<details>
<summary><b>Trường hợp datagram bị mất thì sao?</b></summary>

Client đặt `setSoTimeout(3000)`. Hết 3 giây không có reply → in cảnh báo và cho người dùng thử
lại. Đây là hạn chế cố hữu của UDP mà bài tập muốn nhận ra. Muốn chắc chắn hơn thì phải thêm
cơ chế sequence number + ACK/retry như TCP.
</details>

<details>
<summary><b>Tại sao dùng SHA-256 mà không phải lưu mật khẩu rõ?</b></summary>

Không bao giờ lưu mật khẩu dạng rõ vì nếu thư mục dữ liệu bị lộ thì mọi mật khẩu cũng lộ. SHA-256
giữ được tính chất "một chiều". Tuy nhiên SHA-256 **không có salt** nên vẫn yếu hơn hẳn: kẻ tấn
công có thể dùng rainbow table tra cứu ngược. Chuẩn là PBKDF2 / bcrypt / Argon2 kèm salt ngẫu
nhiên — xem §3.4.3 trong `LY_THUYET.md`.
</details>

<details>
<summary><b>Nếu 2 client cùng lúc REGISTER một tên thì sao?</b></summary>

Đã xử lý. `Files.createDirectory()` là thao tác nguyên tử: chỉ một thread thắng, các thread còn
lại nhận `FileAlreadyExistsException` và được trả về mã `409` (không phải `500`). Test 15 client
đồng thời cho kết quả đúng 1 `200` + 14 `409`, và `accounts.dat` chỉ có 1 dòng cho tên đó.
</details>

<details>
<summary><b>Email thực tế có dùng UDP không?</b></summary>

Không. SMTP dùng TCP vì email mất là mất thông tin, cần đảm bảo thứ tự và toàn vẹn. Đề bài này
dùng UDP **để luyện kỹ năng lập trình socket tầng transport**, đúng phạm vi môn học — không phải
để chứng minh UDP là lựa chọn đúng cho email.
</details>

---

## 13. Tài liệu liên quan

| File | Nội dung |
|---|---|
| [`LY_THUYET.md`](LY_THUYET.md) | Lý thuyết nền tảng: mô hình mô phỏng, OSI/TCP-IP, so sánh TCP/UDP, UDP datagram, hệ thống email (SMTP/POP3/IMAP/MIME), RFC 5322, phân tích đề bài, thiết kế protocol, xử lý đồng thời, Câu hỏi vấn đáp |
| `Lab 5. Lap Trinh UDP Socket.txt` | Đề bài gốc (thư mục cha) |