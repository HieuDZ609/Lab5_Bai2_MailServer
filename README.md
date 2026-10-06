# Lab 5 — Bài 2: Mail Server & Mail Client trên UDP

Bài tập lập trình UDP socket: xây dựng **Mail Server** và **Mail Client** bằng Java
hỗ trợ 3 chức năng — tạo tài khoản, gửi email, đăng nhập và xem danh sách email.

| | |
|---|---|
| ![Logo VKU](assets/vku-logo.png) | Logo trường nằm ở `assets/vku-logo.png`, hiển thị trong header của cả hai cửa sổ và làm icon cửa sổ. Xem [§3.1](#31-anh-logo-trong-classpath). |

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
| `LIST\|sent` | Trả về danh sách **bản gửi** của chính tài khoản đó | Người gửi xem lại được mình đã gửi gì (xem [§6.1](#61-hộp-thư-đã-gửi)) |
| `FETCH` | Trả về **nội dung** của một file thư | Để xem được thư; `LOGIN` chỉ trả *tên file* |
| `FETCH\|sent` | Trả về nội dung một bản gửi | Đọc bản gửi mà không đụng bản trong hộp thư đến |

> ⚠️ **`LIST` và `FETCH` không yêu cầu mật khẩu** (xem [§8.5](#85-cảnh-báo-bảo-mật-của-phần-mở-rộng)).
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

# Bắt buộc: copy ảnh logo vào classpath (xem §3.1)
mkdir -p build/assets && cp assets/*.png build/assets/
```

`-encoding UTF-8` là **bắt buộc**, vì code chứa chuỗi tiếng Việt không dấu và có comment tiếng Việt.

### 3.1 Ảnh logo trong classpath

`javac` chỉ biên dịch `.java` sang `.class`, **không** copy tài nguyên. Nên dù lệnh
biên dịch chạy đúng, ảnh trong `assets/` vẫn không nằm trong `build/` và không tìm thấy
qua classpath. Vì vậy phải có bước `cp` ở trên.

`LogoAssets` nạp ảnh theo thứ tự:

1. `getResourceAsStream("/assets/vku-logo.png")` — classpath (bước `cp` ở trên lo phần này);
2. thư mục chứa class đang chạy, thư mục cha của nó, và thư mục làm việc — dự phòng khi
   chạy tay không có bước `cp`.

Nếu mọi cách đều thất bại, `LogoAssets.master()` trả `null`, header **không** gắn logo và
dòng phụ hiện cảnh báo `⚠ thiếu assets/vku-logo.png`. Chương trình vẫn chạy bình thường —
thiếu ảnh không được làm hỏng giao diện, nhưng phải **nhìn thấy được** để không tưởng là
đã có logo.

Kích thước hiển thị đặt ở `Theme.LOGO_H` (72px). Ảnh gốc 960×491 được thu về 282×144 để
lấy mật độ 2× so với kích thước hiển thị.

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
| Thanh tiêu đề | Logo trường VKU + `Mail Server UDP` + tên trường, bên phải là đèn trạng thái (đang chạy / đã dừng) |
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
| Cột trái — **Hộp thư** | **Thư đến** của tài khoản đang đăng nhập (**ẩn cho tới khi đăng nhập**). Thư mới có dấu `●` màu nhấn mạnh |
| Cột phải — 5 tab | `Đăng ký` · `Đăng nhập` · `Gửi thư` · `Đọc thư` · `Thư đã gửi` |
| Thanh tiêu đề | Logo trường VKU + `Mail Client UDP` + tên trường; bên phải là đèn trạng thái + tài khoản/địa chỉ đang dùng |

**Giao diện chỉ mở những gì dùng được.** Trước khi đăng nhập chỉ có 2 tab `Đăng ký` / `Đăng nhập`;
thẻ `Hộp thư`, tab `Gửi thư`, tab `Đọc thư`, tab `Thư đã gửi` và nút `Đăng xuất` đều **ẩn**.
Sau khi đăng nhập thì cả 5 tab và thẻ `Hộp thư` mới hiện, đồng thời tự bật vòng poll 1 giây/lần.

> **Vì sao hộp thư đến nằm ở cột trái mà hộp thư gửi lại là một tab?**
> Hộp thư đến là thứ cần xem *liên tục*, nên đặt sẵn ở cột trái cho nó luôn chiếm
> toàn bộ chiều cao còn trống. Hộp thư gửi chỉ cần xem lại khi cần, nên để thành
> tab — mở ra xong thì ô nhập thư vẫn còn chỗ, không bị bóp còn một dải.

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
| **Đọc thư** | Không cần nhập gì — bấm một tệp trong `Hộp thư` hoặc trong tab `Thư đã gửi` | Tự gọi `FETCH`, hiện header (`Từ` / `Đến` / `Tiêu đề` / `Ngày`) + nội dung; `404` nếu tệp không còn |
| **Thư đã gửi** | Không cần nhập gì — xem/bấm một bản gửi của chính mình | Liệt kê mọi thư bạn đã gửi; bấm vào sẽ mở ở tab `Đọc thư` |

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
| 10 | Cửa sổ B | bấm `Đăng xuất` | Thẻ `Hộp thư` và tab `Gửi thư`/`Đọc thư`/`Thư đã gửi` biến mất, poll dừng |

---

## 6.1. Hộp thư đã gửi

**Vấn đề:** bản gốc chỉ ghi thư vào thư mục **người nhận**. Người gửi gửi xong thì
không xem lại được mình đã gửi gì — nhìn tệp trên máy chủ cũng không phải giao diện
của chương trình, và nhiều khi máy chủ ở máy khác.

**Cách sửa:** mỗi lần gửi thành công, máy chủ lưu thêm **một bản gửi** vào
`data/<người gửi>/sent/`. Đó là *bản sao*, không phải "chuyển thư" — thư thật vẫn
nằm trong hộp thư người nhận.

| | Hộp thư đến (`data/<user>/`) | Hộp thư gửi (`data/<user>/sent/`) |
|---|---|---|
| Sinh ra khi nào | Người khác gửi cho mình | Chính mình gửi đi |
| Có dấu `●` *chưa đọc* | **Có** | **Không** — thư mình tự gửi không có nghĩa "chưa đọc" |
| Có dòng `Receiver-IP` | **Có** (ghi ở lần `FETCH` đầu) | **Không** — bản ghi lại phía người gửi, chưa ai "đọc" nó theo nghĩa đến |

Gửi cho **chính mình** sẽ sinh **hai** tệp: một ở hộp thư đến, một ở hộp thư gửi
(giống Gmail/Outlook). Đánh số tệp **độc lập theo từng hộp thư**, nên bản gửi đầu
tiên có thể là `sent/mail_0001.txt` trong khi hộp thư đến đang ở `mail_0007.txt`.

### Giao thức

Ba lệnh bắt buộc của đề bài **không đổi một byte nào** — `REGISTER`, `LOGIN`, `SEND`
vẫn y hệt. Phần mở rộng nằm ở `LIST`/`FETCH` nhận **thêm một trường tuỳ chọn**:

| Lệnh | Cú pháp | Ví dụ |
|---|---|---|
| Liệt kê hộp thư đến (mặc định) | `LIST\|<user>` · `LIST\|<user>\|inbox` | `LIST\|bob` |
| Liệt kê hộp thư gửi | `LIST\|<user>\|sent` | `LIST\|bob\|sent` |
| Đọc thư đến (mặc định) | `FETCH\|<user>\|<file>` · `FETCH\|<user>\|inbox\|<file>` | `FETCH\|bob\|mail_0001.txt` |
| Đọc bản gửi | `FETCH\|<user>\|sent\|<file>` | `FETCH\|bob\|sent\|mail_0001.txt` |

`SEND` vẫn trả `200|Delivered to '<người nhận>' as file mail_000N.txt` — chỉ tên tệp
**bên người nhận**. Người gửi không cần biết tên tệp bản gửi: vòng poll 1 giây/lần sẽ
tự thấy nó trong `LIST|<user>|sent`.

### Vài điểm bảo mật

- Tên hộp thư được **whitelist** (`inbox`, `sent`), lạ thì `400`. Nhờ vậy
  `LIST|bob|../../etc` không đọc được file ngoài thư mục dữ liệu.
- Tên tài khoản **`sent` bị từ chối**, vì nó trùng tên hộp thư.
- Bản gửi **không bao giờ ghi `Receiver-IP`**, kể cả khi bạn bấm xem nó ở tab `Đọc thư`.
- `SEND` không xác thực người gửi (xem [§8.5](#85-cảnh-báo-bảo-mật-của-phần-mở-rộng)),
  nên `from` là do client tự khai. Máy chủ **chỉ lưu bản gửi khi `from` là tài khoản
  có thật** — nếu không, kẻ xấu gõ `SEND|khongco|...` sẽ tự tạo ra `data/khongco/`
  và biến thư mục đó thành một "tài khoản" trong danh sách của máy chủ.
- Ghi bản gửi là việc **phụ**: nếu ghi lỗi (hết chỗ, không có quyền) thì thư đến
  vẫn giao thành công và `SEND` vẫn trả `200`, chỉ là không có bản gửi.

---

## 7. Cấu trúc thư mục dữ liệu

Sau khi đăng ký `alice`, `bob` và gửi 3 email:

```
data/
├── accounts.dat                 # lưu hash SHA-256 mật khẩu
├── alice/
│   ├── new_email.txt            # file chào mừng (tạo lúc REGISTER)
│   ├── mail_0001.txt            # email bob gửi cho alice
│   └── sent/                    # hộp thư đã gửi (tạo lúc REGISTER)
│       └── mail_0001.txt        # bản gửi của alice
└── bob/
    ├── new_email.txt
    ├── mail_0001.txt
    ├── mail_0002.txt
    └── sent/
        └── mail_0001.txt
```

> `sent/` **tạo sẵn** lúc `REGISTER` nên danh sách hộp thư gửi luôn có sẵn hàng.
> Tài khoản tạo từ trước khi có tính năng này thì thiếu thư mục này — `LIST|<user>|sent`
> trả về danh sách rong thay vì báo lỗi, và thư mục được tạo khi gửi tệp đầu tiên.

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
| `LIST` *(mở rộng)* | `LIST\|<user>` · `LIST\|<user>\|inbox` · `LIST\|<user>\|sent` | `200\|<f1>~<f2>~…~<fn>` | `400\|…` (tên sai / hộp thư lạ)<br>`404\|Account not found` |
| `FETCH` *(mở rộng)* | `FETCH\|<user>\|<file>` · `FETCH\|<user>\|inbox\|<file>` · `FETCH\|<user>\|sent\|<file>` | `200\|<nội dung thư đầy đủ>` | `400\|…`<br>`404\|File not found` |

### 8.3. Bảng mã trả lời

| Mã | Ý nghĩa | Khi nào dùng |
|---|---|---|
| `200` | OK | Thành công |
| `400` | Bad Request | Sai cú pháp, tên tài khoản không hợp lệ |
| `401` | Unauthorized | Sai mật khẩu |
| `404` | Not Found | Không tìm thấy tài khoản |
| `409` | Conflict | Tài khoản đã tồn tại |
| `500` | Server Error | Lỗi hệ thống (không ghi được file…) |

### 8.4. Định dạng file thư trên đĩa

```
From: <nguồn@mailserver.local>
To: <đích@mailserver.local>
Subject: ...
Date: Mon, 05 Oct 2026 16:08:06 +0700
Sender-IP: 172.16.0.252          ← máy chủ lấy từ datagram SEND, ghi lúc giao thư
Message-ID: <...>
MIME-Version: 1.0
Content-Type: text/plain; charset="UTF-8"
Receiver-IP: 192.168.1.55        ← máy chủ ghi ở lần ĐỌC ĐẦU TIÊN của thư

<nội dung thư>
```

Hai dòng IP phục vụ thống kê vận hành (xem [§10.2](#102-ip-người-gửi--ip-người-nhận)):

| Dòng | Ghi khi nào | Ghi tối đa |
|---|---|---|
| `Sender-IP` | Lúc nhận `SEND` — máy chủ biết chính xác IP người gửi từ `DatagramPacket` | 1 lần, không đổi |
| `Receiver-IP` | Lúc `FETCH` **lần đầu** — IP người nhận chỉ biết được khi thư bị đọc | 1 lần, không đổi |

> **Vì sao `Receiver-IP` ghi lúc đọc chứ không lúc gửi:** khi máy chủ giao thư cho
> `hung01`, nó chỉ biết *tài khoản* nhận, **không biết máy nào** sẽ mở thư đó. Hai máy
> cùng đăng nhập `hung01` thì đều là người nhận hợp lệ. Nên "người nhận" ở đây được hiểu là
> **máy đã đọc thư lần đầu**, và dòng này **không ghi đè** khi thư được mở lần sau.

Thư tạo sẵn từ trước khi có tính năng này (kể cả `new_email.txt` do máy chủ tự sinh) sẽ
**không có** hai dòng trên; GUI hiện `(thư cũ)` thay vì bị bỏ trống.

### 8.5. Cảnh báo bảo mật của phần mở rộng

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
| `Đăng xuất` | Ẩn `Hộp thư` + `Gửi thư` + `Đọc thư` + `Thư đã gửi`, dừng poll, quay về tab `Đăng nhập` |
| Bấm `Gửi thư` khi chưa đăng nhập | Báo lỗi **ở tab `Gửi thư`**, không lẫn sang tab `Đăng ký` |
| Tài khoản có thư mục nhưng mất dòng hash trong `accounts.dat` | `401` — không bỏ qua xác thực |
| Gửi thư cho người khác | Thư tới hộp thư đến của họ **và** bản gửi vào `sent/` của mình |
| Gửi cho chính mình | **Hai** tệp: một ở hộp thư đến, một ở hộp thư gửi |
| `LIST\|<user>\|sent` | Chỉ tên tệp bản gửi, **không** lẫn tệp hộp thư đến, không lộ file tạm |
| `FETCH\|<user>\|sent\|<file>` | Đúng nội dung bản gửi; **không** gán `Receiver-IP` vào file |
| Hộp thư lạ (`bogus`) hoặc đường dẫn (`..%2f..`) | `400`, không đọc được file ngoài `data/` |
| Đăng ký tên tài khoản `sent` | `400` — trùng tên hộp thư |
| `SEND` với `from` là tài khoản bịa đặt | `200` cho người nhận, nhưng **không** tạo thư mục tài khoản giả |
| Tài khoản tạo từ trước (thiếu thư mục `sent/`) | `LIST\|<user>\|sent` trả danh sách rong, không lỗi |
| Restart server | Tài khoản và email giữ nguyên |

### Kiểm thử tự động trên tầng GUI

Bộ kiểm thử nằm trong **`test/`**. Chạy hết bằng một lệnh:

```bash
./test/run.sh
```

Script tự biên dịch sạch `src/` vào `build/`, biên dịch `test/` vào `build-test/`, rồi chạy
từng công cụ và báo cáo. Riêng từng công cụ thì chạy tay:

```bash
java -Dfile.encoding=UTF-8 -cp build:build-test E2E        # 155 check
java -Dfile.encoding=UTF-8 -cp build:build-test GeoCheck MailClientFrame 1180 740 login
```

`E2E` chạy chính code của `MailClientFrame` (bấm nút thật qua
`FlatButton.doClick()` → `SwingWorker` → `MailClient` → UDP → `MailServer`) và kiểm tra
phản hồi hiện trên màn hình, thay vì chỉ gọi hàm lõi. Server được mở trong cùng JVM nên
không cần terminal riêng.

Kết quả thu được: **155/155 PASS**, chia làm các nhóm (A–M):

| Nhóm | Nội dung |
|---|---|
| A | `REGISTER` / `LOGIN`: trùng tên, sai định dạng, sai mật khẩu, không tồn tại |
| B | `LIST`: danh sách file, tài khoản sai, tên sai định dạng |
| C | `FETCH`: header đầy đủ, file thiếu, **path traversal**, tài khoản thiếu |
| D | `SEND` rồi `LIST` thấy file mới; `FETCH` giữ đúng xuống dòng và dấu escape |
| E | **Realtime**: client B thấy thư client A gửi, đo thời gian thực tế |
| F | Tài khoản thẻ mục nhưng mất dòng hash → `401`; không đọc được thư của tài khoản ngoài danh sách |
| G | `LOGOUT`: xác nhận `200`, xoá phiên, xoá danh sách thư |
| H | **Cảm giác GUI**: 5/2 tab theo trạng thái, ẩn/hiện thẻ hộp thư, bấm thư ra nội dung, đăng xuất |
| I | Server vẫn phản hồi sau khi client gửi lệnh lỗi |
| K | Người gửi xem được thư mình vừa gửi; thư đến giữa lúc đang đọc không làm mất nội dung đang xem; hai dòng IP trong file thư và trên GUI |
| L | **Hộp thư gửi**: bản gửi nằm đúng thư mục, `LIST`/`FETCH` có folder, tự gửi sinh 2 tệp, bản gửi không có `Receiver-IP`, chặn tên `sent`, chặn folder lạ, `from` giả không tạo tài khoản |
| M | **Tab `Thư đã gửi`**: hiện đủ 5 tab sau đăng nhập, bản gửi **không** lọt vào hộp thư đến, bấm bản gửi ra tab `Đọc thư` với nhãn đúng hộp thư, đăng xuất xoá cả hai danh sách |

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
4. **Thiếu ảnh logo không được làm `InkCheck` báo xanh.** `describe()` gọi mặc định trả
   `null` cho `JLabel` không có chữ, nên nếu không sửa, logo sẽ không bao giờ được đếm
   pixel — mất file `assets/` cũng ra kết quả "mọi chữ đều hiện". Vì vậy `describe()` có
   nhánh riêng cho nhãn có icon, và `checkLogo()` kiểm tra tường minh: ảnh phải nạp được,
   nhãn phải tồn tại, chiều cao phải đúng `Theme.LOGO_H`, và phải vẽ ra tối thiểu 12 pixel
   màu. Chạy `InkCheck` trong thư mục không có `assets/` sẽ báo lỗi và trả exit code 1.

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

`InkCheck` còn kiểm riêng **logo trường**: phải nạp được ảnh, nhãn phải tồn tại đúng chiều cao
`Theme.LOGO_H`, và phải vẽ ra pixel thật (xem điểm 4 ở trên).

Cả hai chạy được ở chế độ **trước** và **sau** đăng nhập (thêm đối số `login`) — sau đăng nhập
mới kiểm được các tab vốn bị ẩn.

> Card của mỗi tab nằm trong `CardLayout`, nên **chỉ tab đang mở mới được vẽ ra**. Vì vậy
> `InkCheck` chạy **từng tab một**: chuyển sang tab, đợi giao diện ổn định, rồi mới đếm
> pixel. Nếu chỉ quét một lần ở tab mặc định thì các tab khác không component nào
> đang hiện ⇒ không được kiểm gì, và một tab hỏng vẫn ra kết quả "xanh".

Kết quả: `InkCheck` xanh ở 16/16 component của server, 98 component của client trước đăng
nhập và 252 component sau đăng nhập (đã gồm cả tab `Thư đã gửi` và logo trường trong header);
`GeoCheck` xanh ở `1000x640`, `1100x700`, `1180x740`, `1280x820`, cả trước và sau đăng nhập.

Header có logo nên cao thêm, vì vậy cũng đã kiểm thủ công hai kích thước nhỏ nhất mà
`run.sh` không chạy: server `720x520` và client `940x640` — đều `HINH HOC SAN`.

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

### 10.1 Bản đồ file

| File | Trách nhiệm |
|---|---|
| `src/Protocol.java` | Định nghĩa hằng số, escape/unescape, tách & ghép request/response, mã trạng thái |
| `src/Mailbox.java` | Nghiệp vụ lưu trữ: tạo account, hash SHA-256, ghi/đọc file email, hai hộp thư `inbox`/`sent` |
| `src/MailServer.java` | UDP socket, listener thread, worker pool, điều phối request → `Mailbox`, đẩy log qua `Consumer<String>` |
| `src/MailClient.java` | Socket UDP phía client, gửi request, nhận & phân tích response |
| `src/Theme.java` | Design token: màu, font, khoảng cách, bo góc; nút phẳng tự vẽ; `LOGO_H` và `logoLabel()` |
| `src/MailServerFrame.java` | Cửa sổ server: cấu hình, Start/Stop, nhật ký real-time |
| `src/MailClientFrame.java` | Cửa sổ client: kết nối, hộp thư đến, 5 tab REGISTER / LOGIN / SEND / READ / SENT, vòng poll, đăng xuất |
| `src/LogoAssets.java` | Nạp `assets/vku-logo.png` (classpath → dự phòng đường dẫn file), tạo icon cửa sổ, cảnh báo khi thiếu ảnh |
| `assets/vku-logo.png` | Logo trường VKU, nền trong suốt, 282×144 (2× kích thước hiển thị) |

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

### 10.2 IP người gửi & IP người nhận

Tab **Đọc thư** có thêm hai dòng, lấy từ header của file thư:

| Dòng hiển thị | Nguồn |
|---|---|
| IP người gửi | `Sender-IP` — máy chủ ghi lúc giao thư, biết từ IP của datagram `SEND` |
| IP người nhận | `Receiver-IP` — máy chủ ghi ở lần đọc đầu tiên |

Muốn xem trên mạng thật, chạy server ở một máy và client ở máy khác cùng Wi-Fi (xem
[§11](#11-khắc-phục-sự-cố)). Hai dòng IP sẽ hiện **khác nhau**, và đó là cách chứng minh
bài chạy đúng qua mạng chứ không phải chỉ trên `localhost`.

> `Sender-IP` là dữ liệu **tự khai** trong nội dung thư: máy gửi không thể tự chứng minh IP
> của nó, chỉ máy chủ mới biết chắc. Với giao thức thật, IP phải đến từ tầng mạng
> (`X-Originating-IP` trong SMTP) chứ không tin vào dữ liệu người dùng gửi.

**Ghi chú khi thêm hàng mới vào tab `Đọc thư`:** phần thân thư, nút và dòng kết quả lấy chỉ số
hàng từ biến đếm `row` của khối header, **không ghi số cứng**. `GridBagLayout` cho phép nhiều
component cùng một ô và sẽ *chia đôi chiều cao ô đó* chứ không báo lỗi — nên ghi số cứng rất
dễ tạo ra lỗi "hai dòng bị tụt xuống dưới, vùng nội dung bị bóp" mà nhìn bằng mắt rất khó
phát hiện. Chi tiết ở [§10.2](#102-ip-người-gửi--ip-người-nhận).

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