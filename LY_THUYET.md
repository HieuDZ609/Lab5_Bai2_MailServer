# LÝ THUYẾT: MẠNG MÁY TÍNH & HỆ THỐNG EMAIL

> Tài liệu tham khảo cho **Lab 5 – Bài 2: Xây dựng chương trình Mail Server (UDP Socket)**
> Môn học: Lập trình Mạng (24IT062) – Bài giảng 5: *Lập trình với giao thức UDP*

---

## MỤC LỤC

**PHẦN 1 — NỀN TẢNG MẠNG**
1. [Mô hình phân tầng và vị trí của bài lab](#11-mô-hình-phân-tầng-và-vị-trí-của-bài-lab)
2. [Mô hình Client/Server](#12-mô-hình-clientserver)
3. [Socket – khái niệm cốt lõi](#13-socket--khái-niệm-cốt-lõi)
4. [UDP – đặc điểm và hệ quả thiết kế](#14-udp--đặc-điểm-và-hệ-quả-thiết-kế)
5. [Khuôn dạng UDP datagram (RFC 768)](#15-khuôn-dạng-udp-datagram-rfc-768)
6. [API Java cho lập trình UDP](#16-api-java-cho-lập-trình-udp)

**PHẦN 2 — HỆ THỐNG EMAIL THẬT**
7. [Kiến trúc hệ thống email](#21-kiến-trúc-hệ-thống-email)
8. [Giao thức SMTP](#22-giao-thức-smtp--ngôn-ngữ-của-email)
9. [Địa chỉ email và vai trò của DNS](#23-địa-chỉ-email-và-vai-trò-của-dns)
10. [POP3 và IMAP – lấy mail về thế nào](#24-pop3-và-imap--lấy-mail-về-thế-nào)
11. [Định dạng email: RFC 5322 và MIME](#25-định-dạng-email-rfc-5322-và-mime)
12. [Bảo mật email](#26-bảo-mật-email)
13. [Maildir – định dạng lưu trữ mailbox](#27-maildir--định-dạng-lưu-trữ-mailbox)

**PHẦN 3 — ÁP DỤNG VÀO BÀI LAB**
14. [Phân tích 3 yêu cầu chức năng của đề bài](#31-phân-tích-3-yêu-cầu-chức-năng-của-đề-bài)
15. [Thiết kế giao thức ứng dụng](#32-thiết-kế-giao-thức-ứng-dụng)
16. [Đặt tên file email](#33-đặt-tên-file-email)
17. [Bảo mật – các lỗ hổng cần phòng chống](#34-bảo-mật--các-lỗ-hổng-cần-phòng-chống)
18. [Xử lý đồng thời trên server](#35-xử-lý-đồng-thời-trên-server)

**PHẦN 4 — TỔNG HỢP**
19. [Bảng đối chiếu hệ thống thật ↔ bài lab](#41-bảng-đối-chiếu-hệ-thống-thật--bài-lab)
20. [Câu hỏi thường gặp khi bảo vệ bài](#42-câu-hỏi-thường-gặp-khi-bảo-vệ-bài)

---

# PHẦN 1 — NỀN TẢNG MẠNG

## 1.1 Mô hình phân tầng và vị trí của bài lab

Bài lab này nằm ở **tầng 4 (Transport)** của mô hình TCP/IP, đồng thời tự thiết kế tầng ứng dụng.

```
┌─────────────────────────────────────────────────────────────────┐
│ Tầng 7   ỨNG DỤNG      ← BẠN TỰ VIẾT: giao thức Mail (đề bài)│
│ Tầng 6   Trình bày     ← Không dùng (GUI nếu có)               │
│ Tầng 5   Phiên         ← Không dùng                              │
│ Tầng 4   TRANSPORT     ← ★ UDP — ĐỘT NÀY HỌC                  │
│ Tầng 3   Mạng          ← IP, định tuyến (router)                │
│ Tầng 2   Liên kết       ← Ethernet, ARP, MAC                    │
│ Tầng 1   Vật lý        ← Cáp đồng, cáp quang, sóng radio       │
└─────────────────────────────────────────────────────────────────┘
```

**Điểm quan trọng:** Tầng 4 cung cấp dịch vụ truyền dữ liệu giữa hai *điểm cuối* (endpoint).
Ứng dụng ở tầng 7 chỉ thấy "gửi 1 message" và "nhận 1 message" — **không cần biết** dữ liệu
đi qua bao nhiêu router, bao nhiêu chặng, có bị nghẽn chặp nào hay không.

### Mô hình OSI 7 tầng và ánh xạ sang TCP/IP

| Tầng OSI | Tầng TCP/IP | Tên gọi | Ví dụ giao thức | Thực hiện bởi |
|---|---|---|---|---|
| 7 | 7 | Ứng dụng (Application) | HTTP, SMTP, FTP, DNS | **Lập trình viên** |
| 6 | — | Trình bày (Presentation) | Mã hóa, nén | Thư viện |
| 5 | — | Phiên (Session) | Quản lý phiên | Thư viện |
| 4 | 4 | **Truyền tải (Transport)** | **TCP, UDP** | **Lập trình viên** |
| 3 | 3 | Mạng (Network) | IP, ICMP, ARP | Hệ điều hành |
| 2 | 2 | Liên kết dữ liệu (Data Link) | Ethernet, Wi-Fi 802.11 | NIC + driver |
| 1 | 1 | Vật lý (Physical) | Cáp, sóng | Phần cứng |

> **Phạm vi môn học** (Bài 1 slide 9): *"Tập trung vào kỹ thuật lập trình sử dụng dịch vụ tại tầng
> transport để xây dựng các ứng dụng mạng."* → Bài lab này nằm đúng phạm vi đó.

---

## 1.2 Mô hình Client/Server

Bài giảng 3 định nghĩa mô hình ứng dụng Client/Server — mô hình phổ biến nhất, áp dụng cho
Email, FTP, Web.

```
┌──────────────────────────────────────────────────┐
│                 SERVER  192.168.0.1:80           │
│         (quản lý tài nguyên, cung cấp dịch vụ)   │
└───────────────▲──────────────────▲────────────────┘
                │                  │
        ┌───────┴──────┐   ┌───────┴──────┐
        │ CLIENT       │   │ CLIENT       │
        │ 192.168.0.2  │   │ 192.168.0.3  │
        └──────────────┘   └──────────────┘
   (giao tiếp người dùng, yêu cầu dịch vụ)
```

### So sánh thành phần

| | Server | Client |
|---|---|---|
| **Vai trò** | Quản lý nguồn tài nguyên, cung cấp & phân phối dịch vụ | Giao tiếp với người dùng, phát sinh yêu cầu |
| **Khởi tạo liên lạc** | **Chờ sẵn** (passive) | **Nói trước** (*speaks first*) |
| **Vòng lặp** | Lặp vô hạn: chờ → xử lý → đáp | Chờ người dùng → gửi → chờ kết quả |

### Đặc trưng (Bài 3 slide 9–12)

1. **Giao thức bất đối xứng** — quan hệ một chiều: client bắt đầu, server sẵn sàng chờ.
2. **Đóng gói dịch vụ** (service encapsulation) — server như một chuyên gia; có thể nâng cấp
   server mà không ảnh hưởng client.
3. **Tính toàn vẹn** — server kiểm soát tài nguyên, client không tự truy cập.
4. **Một tiến trình có thể vừa là server vừa là client.**
5. **Client và server có thể chạy cùng hay khác trạm (host).**

> Trong bài Lab này, **server là nguồn sự thật duy nhất** (single source of truth) về toàn bộ
> dữ liệu mail. Mọi thay đổi đều phải đi qua server — không có cơ chế đồng bộ cục bộ.

---

## 1.3 Socket – khái niệm cốt lõi

> **Socket = một điểm cuối truyền thông (endpoint) của một quá trình.**

Về mặt khái niệm, socket được định danh bởi cặp **(Địa chỉ IP, Số hiệu cổng)**.

### 1.3.1 Vai trò của Port Number

Vì một máy có thể chạy **nhiều ứng dụng cùng lúc**, cần một con số để phân biệt "dữ liệu này
đi vào ứng dụng nào". Port là số nguyên **16 bit**, dải **0 → 65535**.

| Dải port | Tên gọi | Quyền | Mục đích |
|---|---|---|---|
| 0 – 1023 | **Well-known** | Cần quyền admin/root | Dịch vụ hệ thống chuẩn |
| 1024 – 49151 | **Registered** | Bình thường | Ứng dụng đăng ký tên (ghi vào IANA) |
| 49152 – 65535 | **Dynamic/Ephemeral** | Bình thường | Do OS tự cấp cho client |

### 1.3.2 Bảng các port well-known đáng nhớ

| Port | Dịch vụ | Giao thức | Mức |
|---|---|---|---|
| 20 / 21 | FTP (data / control) | TCP | Truyền file |
| 22 | SSH | TCP | Shell từ xa |
| 25 | SMTP | TCP | **Server ↔ Server** (chuyển mail) |
| 53 | DNS | UDP/TCP | Phân giải tên miền |
| 67 / 68 | DHCP | UDP | Cấp cấu hình IP |
| 80 | HTTP | TCP | Web |
| 110 | POP3 | TCP | **Lấy mail (tải về)** |
| 143 | IMAP | TCP | **Lấy mail (đồng bộ)** |
| 465 | SMTPS | TCP | SMTP mã hóa ngay từ đầu |
| 587 | Submission | TCP | Client gửi mail đi |
| 993 / 995 | IMAPS / POP3S | TCP | Bản mã hóa của IMAP/POP3 |

### 1.3.3 Cơ chế sinh port động của client

```
                    Server                          Client
                    IP: 192.168.1.1
                    Port: 2346 (cố định, do code chỉ định)
                          ▲
                          │  datagram có đích = 192.168.1.1:2346
                          │
                    IP: 192.168.1.10
                    Port: 54321 (OS tự cấp ngẫu nhiên)
```

### 1.3.4 ⚠️ Nguyên tắc VÀNG của lập trình UDP server

> Khi server gọi `recvfrom()`, nó nhận được **địa chỉ IP + port nguồn của client** nằm ngay
> trong chính `DatagramPacket` vừa nhận. Server **phải** `sendto()` về **đúng** địa chỉ lấy
> được từ đó.

```java
// ✅ ĐÚNG — dùng địa chỉ lấy từ packet vừa nhận
DatagramSocket serverSocket = new DatagramSocket(2346);
byte[] buf = new byte[8192];
DatagramPacket pkt = new DatagramPacket(buf, buf.length);
serverSocket.receive(pkt);                        // chặn

InetAddress clientIP   = pkt.getAddress();        // ← IP người gửi
int         clientPort = pkt.getPort();            // ← port người gửi

byte[] reply = "200|OK".getBytes(StandardCharsets.UTF_8);
serverSocket.send(new DatagramPacket(reply, reply.length, clientIP, clientPort));
```

**Hậu quả nếu làm sai:** nếu bạn tạo một port ngẫu nhiên riêng (ví dụ `new DatagramSocket(40000)`)
để trả lời, hoặc gửi về `localhost` cố định — **client sẽ không bao giờ nhận được reply** vì nó
đang chờ ở port 54321.

---

## 1.4 UDP – đặc điểm và hệ quả thiết kế

### 1.4.1 Định nghĩa (Bài 5 slide 3)

UDP (User Datagram Protocol) là giao thức **phi kết nối** (connectionless):

- **Không thiết lập kết nối** trước khi truyền (connectionless)
- Các gói dữ liệu được gửi **độc lập**, gọi là **datagram**
- **Không bảo đảm toàn vẹn dữ liệu và thứ tự**
- **Không có cơ chế báo nhận** (acknowledgment)
- Có cơ chế **gán và quản lý số hiệu cổng** để định danh ứng dụng trên một trạm
- **Ít chức năng phức tạp → xu hướng hoạt động nhanh hơn TCP**
- Dùng cho ứng dụng **không đòi hỏi độ tin cậy cao** khi truyền

> **Ẩn dụ trong slide:** *"Cơ chế hoạt động tương tự như gửi một lá thư thông qua dịch vụ bưu điện"*
> — thả thư vào bưu điện, không biết khi nào tới, không biết tới bao nhiêu lá.

### 1.4.2 Quy trình tạo ứng dụng UDP socket

```
SERVER:  socket()  →  bind()  →  recvfrom()  →  sendto()  →  close()
CLIENT:  socket()  →          sendto()  →  recvfrom()  →  close()
```

**Lưu ý quan trọng (Bài 5 slide 7):**
- **Client không thiết lập kết nối** đến server → kết nối không cần thiết
- **Server không chấp nhận kết nối** → chờ và lắng nghe không cần thiết, không tồn tại khái
  niệm "accept"

### 1.4.3 So sánh UDP và TCP

| Tiêu chí | UDP | TCP |
|---|---|---|
| Kết nối | Phi kết nối | Hướng kết nối, 3-way handshake |
| Tin cậy | ✗ Không đảm bảo | ✓ ACK + timeout + retransmit |
| Thứ tự | ✗ Có thể tới lộn xộn | ✓ Đảm bảo đúng thứ tự |
| Tốc độ | ✓ Nhanh, header 8 byte | Chậm hơn, header 20 byte |
| Header | 8 byte | 20–60 byte |
| Phát sóng | ✓ Hỗ trợ multicast/broadcast | ✗ Không |
| Ứng dụng | DNS, DHCP, Video call, Game | Web, Email, File transfer |
| Ranh giới message | ✓ Datagram tự định nghĩa ranh giới (mỗi `send()` = 1 datagram nguyên vẹn) | ✗ Byte-stream không giữ ranh giới, phải tự định nghĩa |

### 1.4.4 ⚠️ 5 hệ quả thực tế khi code bài Mail Server

| # | Đặc điểm UDP | Hệ quả trong bài này | Cách xử lý trong code |
|---|---|---|---|
| 1 | **Mất gói** (loss) | Request `REGISTER` mất → client treo vô hạn | `setSoTimeout()` + báo lỗi, cho phép gửi lại |
| 2 | **Có ranh giới datagram** (nhưng không có ranh giới *logic*) | Một `send()` = đúng 1 datagram, không bị cắt hay gộp | Datagram đã nguyên vẹn → chỉ cần tách **trường** trong đó (§3.2) |
| 3 | **Payload tối đa 65507 byte** | Email dài hơn sẽ không gửi được | Giới hạn độ dài mỗi trường và body |
| 4 | **Không đúng thứ tự** | Reply có thể tới trước request | Xử lý tuần tự: 1 request → 1 reply |
| 5 | **Trùng lặp** (duplicate) | Client retry → tạo account/file 2 lần | Kiểm tra đã tồn tại, đánh số file |

**Tính lại con số 65507:**

```
Kích thước IP packet tối đa        = 65535 byte
  − IP header                     =  20 byte
  ─────────────────────────────────────────
Kích thước UDP payload tối đa      = 65515 byte
  − UDP header                     =   8 byte
  ─────────────────────────────────────────
Kích thước 1 UDP datagram tối đa  = 65507 byte   ← con số này
```

### 1.4.5 Ứng dụng nào dùng UDP, ứng dụng nào dùng TCP

| Ứng dụng | Giao thức | Tại sao |
|---|---|---|
| DNS (phân giải tên) | UDP | Truy vấn nhỏ, nhanh, hỏi lại được |
| DHCP (cấu IP) | UDP | Server lắng nghe chưa có IP cho client |
| Video call / VoIP | UDP | Trễ thấp quan trọng hơn độ chính xác |
| Game online | UDP | Trạng thái cũ không cần gửi lại |
| **Email (SMTP)** | **TCP** | **Không được mất, thứ tự phải đúng** |
| Web (HTTP) | TCP | Không mất góc trang |
| Truyền file (FTP) | TCP | Toàn vẹn file là bắt buộc |

---

## 1.5 Khuôn dạng UDP datagram (RFC 768)

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|          Source Port          |       Destination Port        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|            Length             |           Checksum            |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                                                               |
|                    Payload (dữ liệu ứng dụng)                 |
|                          ...                                  |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

| Trường | Kích thước | Ý nghĩa |
|---|---|---|
| Source Port | 16 bit (2 byte) | Port của tiến trình gửi |
| Destination Port | 16 bit (2 byte) | Port của tiến trình nhận |
| Length | 16 bit (2 byte) | **Tổng chiều dài header + payload** |
| Checksum | 16 bit (2 byte) | Phát hiện lỗi truyền — **Internet checksum** (tổng bù một của các từ 16 bit), *không phải* CRC-16 |

**So sánh với TCP segment (20 byte header):**

```
TCP: Source Port | Dest Port | Seq(4) | Ack(4) | Offset/Flags(2) | Window(2) | Checksum(2) | Urgent(2)
UDP: Source Port | Dest Port | Length(2) | Checksum(2)                              ← chỉ 8 byte
```

**Điểm cốt lõi:** trường `Length` của UDP chính là cơ chế **tự định nghĩa ranh giới message**.
`receive()` trả về **đúng một datagram nguyên vẹn** — không bao giờ cắt hay gộp data như TCP.
Việc còn lại là định nghĩa ranh giới **cho nội dung có cấu trúc bên trong** (nhiều trường
logic) → phải tự thiết kế thêm (§3.2).

---

## 1.6 API Java cho lập trình UDP

### 1.6.1 Hai lớp cốt lõi

| Lớp | Vai trò |
|---|---|
| **`DatagramSocket`** | Điểm cuối truyền thông (gắn với một port). Có thể gửi VÀ nhận. |
| **`DatagramPacket`** | Đơn vị dữ liệu. Chứa dãy byte **+ địa chỉ IP + số cổng**. |

### 1.6.2 Mẫu UDP Server

```java
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class UDPServer {
    public static void main(String[] args) throws Exception {
        DatagramSocket serverSocket = new DatagramSocket(9876);   // socket() + bind()
        byte[] receiveData = new byte[1024];
        byte[] sendData;

        while (true) {
            DatagramPacket receivePacket =
                    new DatagramPacket(receiveData, receiveData.length);
            serverSocket.receive(receivePacket);                  // CHẶN tới khi có gói tin

            InetAddress IPAddress = receivePacket.getAddress();
            int port = receivePacket.getPort();

            sendData = "Text from Server".getBytes(StandardCharsets.UTF_8);
            DatagramPacket sendPacket =
                    new DatagramPacket(sendData, sendData.length, IPAddress, port);
            serverSocket.send(sendPacket);
        }
    }
}
```

### 1.6.3 Mẫu UDP Client

```java
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class UDPClient {
    public static void main(String[] args) throws Exception {
        DatagramSocket clientSocket = new DatagramSocket();   // port ngẫu nhiên, KHÔNG bind
        InetAddress IPAddress = InetAddress.getByName("localhost");

        byte[] sendData = "Text from Client".getBytes(StandardCharsets.UTF_8);
        DatagramPacket sendPacket =
                new DatagramPacket(sendData, sendData.length, IPAddress, 9876);
        clientSocket.send(sendPacket);                          // gửi yêu cầu

        byte[] receiveData = new byte[1024];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        clientSocket.receive(receivePacket);                    // nhận phản hồi

        String response = new String(receivePacket.getData(), 0,
                receivePacket.getLength(), StandardCharsets.UTF_8);
        System.out.println(response);
        clientSocket.close();
    }
}
```

### 1.6.4 ⚠️ 5 lỗi phổ biến của sinh viên

**Lỗi 1 — Bỏ qua `Charset`, dùng mặc định của OS**

```java
// ❌ SAI — dùng charset mặc định của OS (Windows: cp1252/Tiếng Việt lỗi dấu)
byte[] out = msg.getBytes();
String in  = new String(pkt.getData());

// ✅ ĐÚNG — luôn chỉ định UTF-8 cho dữ liệu trên dây (wire format)
byte[] out = msg.getBytes(StandardCharsets.UTF_8);
String in  = new String(pkt.getData(), 0, pkt.getLength(), StandardCharsets.UTF_8);
```

Hệ quả: máy Windows và máy Linux **giải mã khác nhau** → tiếng Việt bị lỗi dấu hoặc đọc ra
ký tự lạ (`?`, `Ã¡`, `á`).

**Lỗi 2 — Dùng `getData()` mà không giới hạn `getLength()`**

```java
// ❌ SAI — trả về TOÀN BỘ buffer 8192 byte, gồm rác ở cuối
String msg = new String(pkt.getData(), StandardCharsets.UTF_8);

// ✅ ĐÚNG — chỉ đọc đúng phần có dữ liệu
String msg = new String(pkt.getData(), 0, pkt.getLength(), StandardCharsets.UTF_8);
```

**Lỗi 3 — Không đặt timeout, chương trình treo vô hạn**

```java
// ✅ ĐÚNG
socket.setSoTimeout(3000);   // 3000 ms
try {
    socket.receive(pkt);
} catch (SocketTimeoutException e) {
    // server không phản hồi -> xử lý, đừng để chương trình treo
}
```

**Lỗi 4 — Dùng `byte[256]` khi dữ liệu có thể dài hơn → cắt cụt (truncated)**

```java
byte[] buf = new byte[8192];   // bộ đệm đủ lớn cho file danh sách mail dài
```

**Lỗi 5 — Gửi reply về địa chỉ cố định thay vì địa chỉ lấy từ `recvfrom`**

```java
// ❌ SAI
serverSocket.send(new DatagramPacket(out, out.length,
        InetAddress.getByName("localhost"), 9999));

// ✅ ĐÚNG — trả về đúng chỗ người gửi đang chờ
serverSocket.send(new DatagramPacket(out, out.length, pkt.getAddress(), pkt.getPort()));
```

### 1.6.5 Bảng tra nhanh thuộc tính `DatagramPacket`

| Phương thức | Ý nghĩa |
|---|---|
| `getAddress()` | Trả về `InetAddress` của người gửi |
| `getPort()` | Port của người gửi |
| `getLength()` | **Số byte dữ liệu thực sự** trong datagram |
| `getData()` | Trả về mảng byte nền (chứa cả rác) |
| `setAddress()` / `setPort()` | Thiết lập đích khi gửi |
| `getSocketAddress()` | Cả IP + port một lần |

### 1.6.6 Ghi/đọc file tiếng Việt đúng chuẩn trong Java

```java
// ✅ Ghi UTF-8
Files.writeString(path, content, StandardCharsets.UTF_8);

// ✅ Đọc UTF-8
String content = Files.readString(path, StandardCharsets.UTF_8);
```

> ⚠️ **Không dùng** `FileReader`/`FileWriter` mặc định (chúng dùng charset của OS → hay lỗi
> dấu tiếng Việt trên Windows). Luôn dùng `Files.writeString(path, s, StandardCharsets.UTF_8)`.

---

# PHẦN 2 — HỆ THỐNG EMAIL THẬT

## 2.1 Kiến trúc hệ thống email

### 2.1.1 Sơ đồ tổng thể

```
┌──────────┐         ┌──────────────┐          ┌──────────────┐        ┌───────────┐
│   MUA    │  SMTP   │  Submission  │  SMTP    │  MTA đích    │        │   MDA     │
│          │  :587   │    Server    │  :25     │  (Postfix)   │───────►│(ghi vào   │
│Thunder-  ├────────►│   (Postfix)  │───────MX─►│  nhận theo    │        │mailbox)   │
│ bird,    │         │              │          │  tên domain  │        └─────┬─────┘
│ Outlook) │         └──────┬───────┘          └──────┬───────┘              │
└────▲─────┘                │                         │                      │
     │                      │ DNS: tra cứu MX         │ DNS: tra cứu        │
     │ IMAP :143/:993       │ (mail exchanger)        │ A / AAAA (IP host)   │
     │ POP3 :110/:995       ▼                         ▼                      ▼
     │                ┌──────────────┐         ┌──────────────┐        ┌──────────────┐
     └────────────────┤  Mail Store  │         │  Mail Store  │───────►│  Maildir:    │
                    │  (Spool)     │         │              │        │  new/ cur/   │
                    └──────────────┘         └──────────────┘        │  tmp/        │
                                                                       └──────────────┘
```

### 2.1.2 Bốn thành phần chính

| Ký hiệu | Tên đầy đủ | Nhiệm vụ | Ví dụ phần mềm |
|---|---|---|---|
| **MUA** | Mail User Agent | Giao diện người dùng: soạn, đọc, gửi, xóa | Thunderbird, Outlook, Apple Mail |
| **MTA** | Message Transfer Agent | Chuyển mail **giữa các máy chủ** qua SMTP | Postfix, Exim, Sendmail |
| **MDA** | Message Delivery Agent | Ghi mail cuối cùng vào **hộp thư** người nhận | procmail, maildrop |
| **MRA** | Message Retrieval Agent | Cung cấp dịch vụ đọc mail (**POP3/IMAP**) cho MUA | Dovecot, Courier |

### 2.1.3 Đặc trưng quan trọng

1. **Không có mô hình client/server 1-1** — một người gửi, nhiều máy chủ trung gian.
2. **Hàng đợi (queue)** — mỗi MTA có hàng đợi riêng; thất bại thì retry nhiều lần trong nhiều giờ/ngày.
3. **Store-and-forward** — máy chủ nhận mail, lưu vào đĩa, **sau đó** mới chuyển tiếp. Nhờ vậy
   người gửi không cần giữ máy bật.
4. **Không đồng bộ hoàn toàn** — không có thời điểm xác nhận tuyệt đối "đã đọc" (đó là lý do
   có **read receipt** nhưng không tin cậy được).

---

## 2.2 Giao thức SMTP – "ngôn ngữ" của email

### 2.2.1 Định nghĩa

**SMTP (Simple Mail Transfer Protocol)** — RFC 5321.
Chạy trên **TCP**:

| Port | Mục đích |
|---|---|
| **25** | MTA ↔ MTA (chuyển mail giữa các server) |
| **587** | MUA ↔ Submission server (client gửi mail đi) |
| **465** | SMTP over TLS (mã hóa ngay từ đầu) |

**Đặc điểm cốt lõi:** giao thức **văn bản**, dựa trên **dòng lệnh** (line-oriented).
Server luôn gửi trước một dòng **mã số 3 chữ số**; client trả lời bằng lệnh.

### 2.2.2 Phiên SMTP đầy đủ (transcript)

```
S: 220 mail.ued.vn ESMTP Postfix                    ← chào, sẵn sàng phục vụ
C: EHLO client1.local                               ← client xin phép nói (ESMTP)
S: 250-mail.ued.vn                                  ← phản hồi từng dòng (hostname)
S: 250-PIPELINING                                   ← khả năng: cho phép gửi nhiều lệnh liền nhau
S: 250-SIZE 10240000                                ← khả năng: giới hạn kích thước mail
S: 250-STARTTLS                                     ← khả năng: có thể nâng cấp lên TLS
S: 250 8BITMIME                                    ← dòng cuối KHÔNG có dấu "-" = hết danh sách
C: STARTTLS
S: 220 2.0.0 Ready to start TLS                     ← báo sẵn sàng bắt tay TLS
    ╔════════════════ BẮT ĐẦU MÃ HÓA TLS ════════════════╗
    ║  Toàn bộ dữ liệu từ đây đi trong kênh mã hóa        ║
    ╚═════════════════════════════════════════════════════╝
C: MAIL FROM:<hieu@ued.vn>                          ← mở giao dịch, khai báo NGƯỜI GỬI
S: 250 2.1.0 Ok
C: RCPT TO:<nguyenvunhai@gmail.com>                ← khai báo NGƯỜI NHẬN (lặp lại được)
S: 250 2.1.5 Ok
C: DATA                                            ← "tôi sắp gửi nội dung mail"
S: 354 End data with <CR><LF>.<CR><LF>              ← mở cửa, yêu cầu client gửi nội dung
C: From: hieu@ued.vn
C: To: nguyenvunhai@gmail.com
C: Subject: Báo cáo Lab 5
C: Date: Fri, 03 Oct 2026 22:30:00 +0700
C: Message-ID: <20261003223000.12345@ued.vn>
C:
C: Xin chào, đây là nội dung email của tôi.
C: .                                               ← DẤU CHẤM ĐƠN LẺ = KẾT THÚC DATA
S: 250 2.0.0 Ok: queued as 4A2B3C1D                 ← đã nhận, đưa vào hàng đợi
C: QUIT
S: 221 2.0.0 Bye                                   ← kết thúc phiên
```

> 📌 **Quy ước quan trọng:** Mỗi dòng trả lời của SMTP có dạng `<code><text>`.
> Nếu có dấu `-` ngay sau code (`250-`) nghĩa là **còn dòng tiếp theo** trong phần trả lời.
> Khi gặp dòng **không có dấu `-`** (`250`) thì phần trả lời kết thúc.

### 2.2.3 Bảng lệnh SMTP

| Lệnh (client → server) | Ý nghĩa | Mã OK |
|---|---|---|
| `HELO <domain>` | Xác định danh tính (bản SMTP 1.0) | `250` |
| `EHLO <domain>` | Xác định danh tính + yêu cầu danh sách khả năng (ESMTP) | `250` |
| `STARTTLS` | Nâng cấp kết nối plaintext → TLS | `220` |
| `AUTH <mechanism>` | Xác thực người dùng | `235` |
| `MAIL FROM:<addr>` | Khai báo địa chỉ người gửi (đầu giao dịch) | `250` |
| `RCPT TO:<addr>` | Khai báo địa chỉ người nhận (lặp nhiều lần được) | `250` / `550` |
| `DATA` | Bắt đầu gửi phần header + body | `354` → `250` |
| `RSET` | Hủy giao dịch hiện tại | `250` |
| `NOOP` | Kiểm tra server còn sống | `250` |
| `QUIT` | Kết thúc phiên | `221` |

### 2.2.4 Bảng mã trả lời SMTP

| Mã | Nhóm | Ý nghĩa | Ví dụ |
|---|---|---|---|
| `2xx` | Thành công | Yêu cầu đã được chấp nhận | `250 OK`, `220 Ready`, `221 Bye` |
| `3xx` | Cần thêm thông tin | Server đang chờ client gửi tiếp | `354 Go ahead` |
| `4xx` | Lỗi **tạm thời** | Hãy thử lại sau | `421 Service unavailable`, `450 Mailbox busy` |
| `5xx` | Lỗi **vĩnh viễn** | Không thử lại được | `550 No such user`, `552 Message too large` |

Chi tiết hơn (dạng `XYZ.SSS`):
- `2.1.0` = thành công, loại 1 (mail), mã 0
- `2.1.5` = thành công, địa chỉ người nhận hợp lệ
- `5.1.1` = người dùng không tồn tại
- `5.2.0` = hộp thư đầy

### 2.2.5 Giao dịch bất đối xứng trong SMTP

Mỗi lần gửi mail là **một giao dịch độc lập** (transaction). Server chỉ "biết" người gửi khi
nhận `MAIL FROM`. Kết thúc bằng `QUIT` hoặc timeout (RFC 5321 quy định 5 phút).

```
MAIL FROM ──► RCPT TO ──► DATA ──► . ──► [chuyển sang giao dịch mới hoặc QUIT]
```

---

## 2.3 Địa chỉ email và vai trò của DNS

### 2.3.1 Cấu trúc địa chỉ

```
    hieu.nguyen  @  ued.vn
         │             │      └── domain: doanh nghiệp/tổ chức sở hữu
         │             └───────── tên miền (FQDN)
         └─────────────────────── local-part: tên hộp thư trong domain
```

Quy ước:
- `@` là ký tự phân cách bắt buộc
- Phần trước `@`: ký tự chữ, số, `.`, `_`, `-`
- Phần sau `@`: nhiều cấp, phân biệt hoa/thường **không** (không phân biệt)

### 2.3.2 DNS phân giải thế nào khi gửi mail

Khi MTA gửi mail cho `ban@gmail.com`, nó phải tra cứu DNS:

| Bản ghi | Ví dụ | Ý nghĩa |
|---|---|---|
| **MX** (Mail Exchange) | `gmail.com. MX 10 mail1.google.com.` | Máy chủ nào nhận mail cho domain này. Số `10` = ưu tiên (nhỏ hơn = ưu tiên cao hơn) |
| **A** / **AAAA** | `mail1.google.com. A 142.250.185.27` | Tên miền → địa chỉ IPv4 / IPv6 |
| **SPF** (TXT) | `gmail.com. TXT "v=spf1 include:_spf.google.com -all"` | Danh sách IP được phép gửi mail tên miền |
| **DKIM** | Chữ ký RSA kèm header | Mail được ký bằng khóa riêng của domain |
| **DMARC** | `_dmarc.gmail.com. TXT "v=DMARC1; p=quarantine"` | Chính sách xử lý khi SPF/DKIM trượt |

> 💡 **Điểm nhỏ:** Nếu domain **không có bản ghi MX**, dịch vụ email thường *fallback* sang bản
> ghi A (dùng chính IP của domain làm máy chủ mail).

### 2.3.3 Ví dụ tra cứu đầy đủ

```
Gửi mail cho: hieu@ued.vn

Bước 1: ued.vn có bản ghi MX không?
        → MX 10 mail1.ued.vn  ✓

Bước 2: mail1.ued.vn có bản ghi A không?
        → A 203.113.128.50  ✓

Bước 3: Kết nối TCP đến 203.113.128.50:25
        → "220 mail1.ued.vn ESMTP Postfix"

Bước 4: Gửi nội dung mail qua kết nối đó
```

---

## 2.4 POP3 và IMAP – lấy mail về thế nào?

Sau khi mail đã nằm trong mailbox trên server, **MUA cần giao thức để đọc/tải mail về**.

### 2.4.1 POP3 (Post Office Protocol v3) — RFC 1939

Chạy trên TCP **110** (POP3S = **995**, TLS ngay từ đầu).

### Phiên POP3 đầy đủ

```
S: +OK POP3 server ready              ← chào (dấu "+" = thành công)
C: USER hieu                          ← gửi tên đăng nhập (PLAINTEXT!)
S: +OK                                ← ok, hãy gửi mật khẩu
C: PASS secret123                     ← gửi mật khẩu
S: +OK                                ← đăng nhập thành công
C: STAT                               ← thống kê chung hộp thư
S: +OK 2 320                          ← có 2 tin, tổng cộng 320 octet
C: LIST                               ← liệt kê chi tiết từng tin
S: +OK 2 messages (320 octets)
S: 1 120                              ← tin số 1: 120 octet
S: 2 200                              ← tin số 2: 200 octet
S: .                                  ← KẾT THÚC danh sách
C: RETR 1                             ← tải nội dung tin số 1
S: +OK 120 octets
S: To: bob@ued.vn                     ← ┐
S: From: alice@ued.vn                 │ trả về nguyên văn mail
S: Subject: Xin chao                  │
S:                                    │
S: Noi dung email...                  │
S: .                                  ← KẾT THÚC nội dung
C: TOP 1 0                            ← xem header tin 1 (0 dòng body)
S: +OK
S: From: alice@ued.vn
S: .
C: DELE 1                             ← đánh dấu xóa tin 1
C: QUIT                               ← kết thúc, server XÁC NHẬN xóa
S: +OK
```

### Bảng lệnh POP3

| Lệnh | Ý nghĩa |
|---|---|
| `USER <name>` | Gửi tên đăng nhập |
| `PASS <pass>` | Gửi mật khẩu |
| `STAT` | Số lượng tin + tổng dung lượng (không tải nội dung) |
| `LIST [n]` | Liệt kê dung lượng từng tin |
| `RETR <n>` | **Tải** toàn bộ tin n (server sẽ đánh dấu đã đọc) |
| `TOP <n> <m>` | Tải header + m dòng đầu body |
| `DELE <n>` | Đánh dấu xóa tin n |
| `NOOP` | Giữ kết nối sống |
| `RSET` | Hủy thay đổi trong phiên (chưa thoát) |
| `QUIT` | Kết thúc phiên, áp dụng xóa |

### 2.4.2 IMAP (Internet Message Access Protocol) — RFC 3501

Chạy trên TCP **143** (IMAPS = **993**).

### Phiên IMAP đầy đủ

```
C: a001 LOGIN hieu secret123
S: a001 OK LOGIN completed                      ← "a001" là TAG do client tự sinh

C: a002 SELECT INBOX                            ← chọn hộp thư
S: * 3 EXISTS                                   ← hộp thư có 3 tin
S: * 0 RECENT                                   ← 0 tin mới
S: * FLAGS (\Answered \Flagged \Deleted \Seen \Draft)
S: * OK [UIDVALIDITY 3857529045] UIDs valid
S: a002 OK [READ-WRITE] SELECT completed         ← READ-WRITE = có thể xóa/di chuyển

C: a003 FETCH 1:3 (UID FLAGS RFC822.SIZE)       ← lấy thông tin tin 1→3
S: * 1 FETCH (UID 3857529046 FLAGS (\Seen) RFC822.SIZE 4456)
S: * 2 FETCH (UID 3857529047 FLAGS () RFC822.SIZE 8123)
S: * 3 FETCH (UID 3857529048 FLAGS () RFC822.SIZE 1024)
S: a003 OK FETCH completed

C: a004 LOGOUT
S: * BYE IMAP4rev1 server logging out
S: a004 OK LOGOUT completed
```

### Bảng lệnh IMAP

| Lệnh | Ý nghĩa |
|---|---|
| `LOGIN <user> <pass>` | Đăng nhập |
| `SELECT <mailbox>` | Chọn hộp thư (mở session trên hộp thư đó) |
| `EXAMINE <mailbox>` | Giống SELECT nhưng **chỉ đọc** |
| `FETCH <set> <items>` | Tải thuộc tính/nội dung tin |
| `STORE <set> <op> <flags>` | Đánh dấu: `\Seen`, `\Answered`, `\Flagged`, `\Deleted` |
| `LIST "" "*"` | Liệt kê thư mục con |
| `CREATE <name>` | Tạo thư mục mới |
| `SEARCH <criteria>` | Tìm kiếm theo tiêu chí |
| `COPY <set> <mailbox>` | Sao chép tin sang thư mục khác |
| `EXPUNGE` | Xóa thật các tin đánh dấu `\Deleted` |
| `LOGOUT` | Kết thúc phiên |

### 2.4.3 So sánh POP3 và IMAP

| Tiêu chí | POP3 | IMAP |
|---|---|---|
| Mô hình | Tải về máy, **xóa khỏi server** | **Giữ trên server**, đồng bộ nhiều thiết bị |
| Thư mục con | ✗ Không | ✓ Có (`CREATE`, `LIST`, `DELETE`) |
| Đọc một phần | ✗ Phải tải cả | ✓ `FETCH ... BODY[]<0.1024>` |
| Đánh dấu đã đọc | Mỗi máy tự quản lý | **Đồng bộ flags** mọi thiết bị |
| Nhiều thiết bị | Không phù hợp | **Bắt buộc** |
| Dùng trên điện thoại | Không ổn | Phải dùng |
| Độ phức tạp | Rất đơn giản | Phức tạp (hàng trăm lệnh) |
| Phổ biến hiện nay | Đang bị bỏ dần | **Chiếm đa số** |

> 📌 **Liên hệ trực tiếp với đề bài:** Yêu cầu thứ 3 của đề — *"mở thư mục tương ứng với account và
> gửi tất cả các tên file của thư mục đó về client"* — **chính là ý tưởng của lệnh `LIST` (POP3)
> và `SELECT` + `FETCH` (IMAP)**. Đề bài chỉ yêu cầu gửi **tên file**, chưa yêu cầu gửi **nội
> dung** → đơn giản hơn hệ thống thật.

### 2.4.4 Dấu chấm `.` — kỹ thuật tự định nghĩa ranh giới

Trong cả POP3 và IMAP, khi server gửi dữ liệu **nhiều dòng** (nội dung mail, kết quả LIST),
server kết thúc bằng một dòng chỉ có dấu chấm:

```
S: +OK 2 messages
S: 1 120
S: 2 200
S: .          ← dấu hiệu "hết dữ liệu"
```

Client phải **tìm dòng `.` đơn lẻ** để biết đã đọc xong. Nếu mail chứa một dòng `.` thật,
server phải **escape** thành `..` (theo RFC).

> 🎓 **Bài học rút ra:** Đây chính là **self-delimiting protocol** — cơ chế mà bài lab của bạn
> cũng phải áp dụng, vì UDP không có "đánh dấu kết thúc message" sẵn có. Xem §3.2.

---

## 2.5 Định dạng email: RFC 5322 và MIME

### 2.5.1 Internet Message Format — RFC 5322

Một email gồm 2 phần ngăn cách bởi **một dòng trống**:

```
From: hieu.nguyen@ued.vn
To: nguyenvunhai@gmail.com
Cc: bientrinh@gmail.com
Subject: Báo cáo Lab 5 - Mail Server
Date: Fri, 03 Oct 2026 22:30:00 +0700
Message-ID: <20261003223000.12345@ued.vn>
In-Reply-To: <20261002110000.99999@ued.vn>
References: <20261002110000.99999@ued.vn>
MIME-Version: 1.0
Content-Type: text/plain; charset="UTF-8"
Content-Transfer-Encoding: 8bit
X-Priority: 3

────────────────────────────────────────────────────  ← DÒNG TRỐNG phân cách
Xin chào,

Đây là nội dung email của tôi.
```

#### Cấu trúc chi tiết

| Phần | Quy tắc |
|---|---|
| **Header** | Chuỗi trường `<Tên trường> : <Giá trị>`, mỗi trường cách nhau bằng `\r\n` |
| **Header bắt buộc** | `From`, `Date`, `Message-ID` |
| **Dòng trống** | `CRLF` rỗng — bắt buộc có, phân tách header/body |
| **Body** | Nội dung, có thể là văn bản thuần hoặc MIME |
| **Header "folded"** | Trường dài được ngắt dòng, dòng sau bắt đầu bằng khoảng trắng |

#### Bảng các trường header quan trọng

| Trường | Ý nghĩa |
|---|---|
| `From` | Địa chỉ người gửi |
| `To` | Địa chỉ người nhận chính |
| `Cc` | Carbon copy — nhận để biết |
| `Bcc` | Blind copy — **không ai thấy**, dùng để gửi bí mật |
| `Reply-To` | Địa chỉ trả lời (khác `From`) |
| `Subject` | Tiêu đề |
| `Date` | Thời gian gửi, định dạng RFC 5322 |
| `Message-ID` | Định danh duy nhất `<timestamp.unique@domain>` |
| `In-Reply-To` | `Message-ID` của mail đang trả lời |
| `References` | Chuỗi `Message-ID` của cả chuỗi hội thoại |

#### Định dạng ngày giờ RFC 5322

```
Fri, 03 Oct 2026 22:30:00 +0700
│    │              │      │  └─ Múi giờ (GMT+7)
│    │              │      └──── Giây (2 chữ số)
│    │              └─────────── Phút, Giờ (2 chữ số)
│    └────────────────────────── Tháng (tên viết tắt tiếng Anh)
└─────────────────────────────── Ngày trong tuần + số ngày, tên tháng, năm
```

### 2.5.2 MIME (Multipurpose Internet Mail Extensions) — RFC 2045–2049

**Vấn đề:** Email gốc chỉ hỗ trợ **ASCII 7-bit** (128 ký tự: chữ, số, ký hiệu cơ bản).
Không gửi được tiếng Việt có dấu, không gửi được file nhị phân.

**Giải pháp:** MIME định nghĩa cách đóng gói nhiều "phần" (part) và mã hóa chúng.

#### Ba kiểu mã hóa nội dung

| Kiểu | Cơ chế | Dùng cho | Ví dụ |
|---|---|---|---|
| **7bit** | Raw, không mã hóa | ASCII thuần | `Hello` |
| **8bit** | Raw, byte mở rộng | UTF-8 (nếu chấp nhận) | `Xin chào` |
| **Quoted-Printable** | Ký tự đặc biệt → `=XX` hex; ngắt dòng bằng dấu `=` cuối dòng | **Văn bản nhiều tiếng Việt** | `Xin ch=E0o` |
| **Base64** | Nhị phân → 64 ký tự `A-Z a-z 0-9 + /`, **chia dòng 76 ký tự** | **File nhị phân** (PDF, ảnh) | `SGVsbG8gd29ybGQ=` |

> 💡 **Vì sao không dùng Base64 cho text tiếng Việt?** Vì nó làm file to thêm ~33% và **không đọc
> được bằng mắt**. Quoted-Printable giữ nguyên phần ASCII, chỉ mã hóa ký tự đặc biệt → phù hợp
> hơn cho văn bản.

#### Ví dụ email có tệp đính kèm (multipart/mixed)

```
MIME-Version: 1.0
Content-Type: multipart/mixed; boundary="----=_Part_001"

This is a multi-part message in MIME format.
------=_Part_001
Content-Type: text/plain; charset="UTF-8"
Content-Transfer-Encoding: 8bit

Xin chào, đính kèm báo cáo Lab 5.
------=_Part_001
Content-Type: application/pdf; name="bao-cao.pdf"
Content-Transfer-Encoding: base64
Content-Disposition: attachment; filename="bao-cao.pdf"

JVBERi0xLjQKJcOkw7zDtsOfCjEgMCBvYmoKPDwvTGVuZ3RoIDMgMCBSL0ZpbHRlci9G
bGF0ZURlY29kZT4+CnN0cmVhbQp4nDPQM1Qo5ypUMABDczMuMCBvYmoKPDwvVHlw
ZS9DYXRhbG9nL1BhZ2VzIDIgMCBSL0NvdW50IDE+PgpzdHJlYW0KQlRJTUU=
------=_Part_001--
```

#### Bảng các kiểu `Content-Type` thường gặp

| Content-Type | Ý nghĩa |
|---|---|
| `text/plain` | Văn bản thuần |
| `text/html` | Trang HTML |
| `application/pdf` | File PDF |
| `image/jpeg` / `image/png` | Ảnh |
| `application/zip` | File nén |
| `audio/mpeg` | File MP3 |
| **`multipart/mixed`** | Nhiều phần khác loại (text + file đính kèm) |
| `multipart/alternative` | Nhiều phiên bản (plain và HTML của cùng 1 nội dung) |
| `multipart/related` | Nhiều phần có liên quan (HTML + ảnh nhúng) |

### 2.5.3 💡 Bài học rút ra cho bài Lab

Đề yêu cầu: *"tạo một file có nội dung là nội dung email đó"*.

Nếu bạn **mô phỏng trung thực** hệ thống email thật, nội dung file nên có dạng header RFC 5322:

```
From: alice@ued.vn
To: bob@ued.vn
Subject: Xin chao
Date: Fri, 03 Oct 2026 22:30:00 +0700

Noi dung email cua Alice gui cho Bob.
```

**Ba lý do nên làm vậy:**

1. **Chứng minh bạn hiểu RFC 5322**, không chỉ biết "gửi một chuỗi". Giáo viên nhìn thấy
   kiến thức thật.
2. **Giải quyết tự nhiên bài toán "xác định gửi đến account nào"** — server chỉ cần đọc trường
   `To:` là biết đích, thay vì phải chọn cấu trúc riêng.
3. **Là cơ sở để mở rộng**: thêm `Cc`, `Bcc`, `Reply-To`, `Subject` vào protocol rất tự nhiên.

---

## 2.6 Bảo mật email

### 2.6.1 Các lớp bảo mật trong email

| Cơ chế | Cổng | Cách hoạt động |
|---|---|---|
| **STARTTLS** (Opportunistic TLS) | 25, 587, 143, 110 | Bắt đầu **plaintext**, nâng cấp lên TLS sau khi gửi lệnh `STARTTLS`. Rủi ro: dữ liệu đầu có thể bị đọc |
| **Implicit TLS** | **465**, **995**, **993** | Mã hóa **ngay từ đầu** kết nối (SMTPS/POP3S/IMAPS). An toàn hơn |
| **SASL** | — | Cơ chế xác thực (PLAIN, LOGIN, CRAM-MD5, OAUTH2) — *trước* khi lệnh `AUTH` |
| **TLS/SSL** | — | Tầng bảo mật bên dưới, mã hóa kênh truyền |
| **PGP / S/MIME** | — | Mã hóa **nội dung mail** (mã hóa đầu-cuối), dùng khóa công khai |

### 2.6.2 ⚠️ Chống giả mạo: SPF, DKIM, DMARC

| Cơ chế | Cách hoạt động | Chống được |
|---|---|---|
| **SPF** (SPF v1, RFC 7208) | DNS `TXT` liệt kê IP được phép gửi mail tên miền đó | **Spoofing địa chỉ From** |
| **DKIM** (RFC 6376) | Ký bằng **khóa riêng RSA** của domain, chữ ký nằm trong header | Giả mạo + sửa nội dung |
| **DMARC** (RFC 7489) | Cho biết **chính sách** khi SPF/DKIM trượt: `p=none` / `p=quarantine` / `p=reject` | Quyết định xử lý mail giả mạo |

### 2.6.3 ⚠️ Vì sao email ngày nay chạy trên TCP chứ không phải UDP?

Đây là **câu hỏi bảo vệ bài rất hay gặp**. Lý do:

| Yêu cầu của email | UDP có? | TCP có? |
|---|---|---|
| Mail **không được mất** | ✗ Không đảm bảo | ✓ ACK + retransmit |
| Mail phải đến **đúng thứ tự** | ✗ | ✓ Số thứ tự |
| Dung lượng mail có thể **rất lớn** (kèm file đính kèm) | ✗ Giới hạn 65507 byte | ✓ Stream không giới hạn |
| Nhiều người nhận, nhiều chặng trung gian | ✗ Dễ mất | ✓ Cơ chế retry bền vững |

**Kết luận:** Email là ứng dụng mà **sai sót rất tệ** (mất mail = mất thông tin quan trọng),
nên bắt buộc dùng TCP.

> 🎓 **Cách trả lời giáo viên:**
> *"Đề bài này mô phỏng nghiệp vụ mail bằng UDP **để luyện kỹ năng lập trình socket ở tầng
> transport** — đúng phạm vi môn học (Bài 1: 'tập trung vào kỹ thuật lập trình sử dụng dịch vụ tại
> tầng transport để xây dựng các ứng dụng mạng'). Nó **không** phải để chứng minh UDP là giao thức
> chọn lựa cho email. Và chính vì UDP không bảo đảm tin cậy, bài tập này buộc em phải tự thiết kế
> framing protocol và cơ chế timeout — đó cũng là bài học quan trọng nhất của UDP."*

---

## 2.7 Maildir – định dạng lưu trữ mailbox

### 2.7.1 Vấn đề với định dạng mbox cũ

Định dạng cổ điển `/var/mail/username` lưu **TẤT CẢ mail trong MỘT file khổng lồ**, nối tiếp nhau.
Nếu muốn thêm mail mới → phải mở file, tìm cuối, append. Nếu máy chủ crash giữa chừng →
**hỏng cả hộp thư**. Không thể xóa riêng một mail mà không phải viết lại toàn bộ file.

### 2.7.2 Định dạng Maildir (hiện đại)

Mỗi hộp thư là **một thư mục chứa 3 thư mục con**, mỗi email là **một file riêng biệt**:

```
/var/vmail/ued.vn/hieu/
├── tmp/     ← đang ghi dở (chưa hoàn tất). File tạm ở đây
├── new/     ← mail MỚI, chưa đọc  ← server ghi mail mới vào đây
│   ├── 1727957400.M123456P789.ued.vn
│   └── 1727957401.M123457P790.ued.vn
└── cur/     ← mail ĐÃ ĐỌC (đã xem thiết bị)
    └── 1727957300.M123455P788.ued.vn,S=4567:2,S
```

### 2.7.3 Giải thích tên file

```
1727957400.M123456P789.ued.vn
   │        │      │      └── hostname của máy chủ lưu mail
   │        │      └───────── PID của tiến trình
   │        └──────────────── số thứ tự để không trùng trong 1 giây
   └───────────────────────── Unix timestamp (giây kể từ 1970-01-01)
```

Thêm `,S=4567` và `,S` khi chuyển từ `new/` sang `cur/` (đánh dấu đã đọc), `:2,S` là chỉ báo
trạng thái cho chương trình đọc mail.

### 2.7.4 Bốn ưu điểm của Maildir

1. **Mỗi email = 1 file** → thêm/xóa không cần đọc/ghi lại cả hộp thư
2. **Chống mất dữ liệu** → file được `fsync` và `rename` (rename là thao tác nguyên tử trên Linux)
3. **Chống trùng lặp** → tên file có timestamp + PID + sequence
4. **Dễ đồng bộ** → có thể đồng bộ bằng rsync giữa nhiều máy

### 2.7.5 💡 Đối chiếu với đề bài

| Maildir (thật) | Đề bài Lab 5 |
|---|---|
| Thư mục `/var/vmail/ued.vn/hieu/` | "tạo một thư mục tương ứng ở máy server" |
| File trong `new/` hoặc `cur/` | "tạo một file có nội dung là nội dung email đó" |
| Tên file có timestamp chống trùng | `mail_0001.txt`, `mail_0002.txt` (đánh số) |
| Lệnh `STATUS`/`LIST` (POP3), `SELECT` (IMAP) | "gửi tất cả các tên file của thư mục đó về client" |
| Dịch chuyển `new/` → `cur/` | (đề không yêu cầu) |

> **Kết luận:** Bài lab là **Maildir rút gọn** — mô phỏng đúng tinh thần cách lưu trữ mailbox
> hiện đại, nhưng chỉ giữ 3 thao tác cơ bản nhất.

---

# PHẦN 3 — ÁP DỤNG VÀO BÀI LAB

## 3.1 Phân tích 3 yêu cầu chức năng của đề bài

### 3.1.1 Bảng đối chiếu nghiệp vụ

| # | Yêu cầu nguyên văn của đề | Nghiệp vụ email thật tương ứng | Thao tác server phải làm |
|---|---|---|---|
| **1** | "Mỗi khi người dùng tạo account mới tại các máy client, thì chương trình server sẽ tạo một thư mục tương ứng ở máy server, đồng thời tạo một file có tên `new_email.txt` trong thư mục đó" | Tạo mailbox + gửi **mail chào mừng** | `mkdir data/<user>` + ghi `new_email.txt` |
| **2** | "Mỗi khi người dùng gửi một email, chương trình server nhận được email đó, **xác định gửi đến account nào**, tại thư mục của account đó chương trình server sẽ tạo một file có nội dung là nội dung email đó" | `MAIL FROM` → `RCPT TO` → `DATA` | Parse trường `To:` → ghi file vào `data/<to>/` |
| **3** | "Mỗi khi người dùng ở máy client đăng nhập vào một account, thì chương trình server phải mở thư mục tương ứng với account đó ở server và gửi **tất cả các tên file** của thư mục đó về client" | `USER`/`PASS` → `LOGIN` + `LIST` | Kiểm tra password → `File.listFiles()` → gửi về |

### 3.1.2 Điểm cốt lõi: cần biết account nào đã tồn tại

Cả yêu cầu 1 và 3 đều cần trả lời câu hỏi: **"account này có tồn tại không?"** → cần một nguồn
dữ liệu về danh sách account.

| Cách | Cách làm | Ưu | Nhược |
|---|---|---|---|
| **1 — Quét thư mục** | `new File(dataDir).listFiles()` | **Bền vững**: tồn tại sau khi tắt server. Không cần đồng bộ | Phải đọc đĩa mỗi lần (chậm hơn, nhưng không đáng kể) |
| **2 — Bảng trong RAM** | `HashMap<String,String>` | Rất nhanh | ⚠️ **Mất sạch khi tắt server** |
| **3 — File cấu hình** | `accounts.txt` | Bền vững, tra cứu nhanh | Phải đồng bộ với thư mục thật |

> 👉 **Khuyến nghị: Cách 1** — quét thư mục. Đơn giản, không có trạng thái lệch (inconsistency),
> và dữ liệu tồn tại bền vững. Nếu muốn nâng cao: cache trong RAM + fallback quét đĩa khi cache miss.

### 3.1.3 Lưu đồ bài toán tổng thể

```
┌──────────┐                              ┌──────────────────────────────┐
│  CLIENT  │                              │         SERVER (UDP :2346)   │
└────┬─────┘                              └──────────────┬───────────────┘
     │                                                    │
     │  ① REGISTER|alice|123                            │
     ├───────────────────────────────────────────────────►│
     │                                    validate username (regex)
     │                                    check account đã tồn tại?
     │                                    mkdir  data/alice/
     │                                    write data/alice/new_email.txt
     │  ◄──────────────────────────────────────────────  │
     │  200|Account alice created                        │
     │                                                    │
     │  ② SEND|alice|bob|Xin chao|Noi dung...           │
     ├───────────────────────────────────────────────────►│
     │                                    validate from/to
     │                                    check data/bob tồn tại?
     │                                    next = mail_0001.txt
     │                                    write data/bob/mail_0001.txt
     │                                                    │
     │  ◄──────────────────────────────────────────────  │
     │  200|Delivered to bob                             │
     │                                                    │
     │  ③ LOGIN|bob|123                                  │
     ├───────────────────────────────────────────────────►│
     │                                    check account tồn tại?
     │                                    check password
     │                                    list = data/bob.listFiles()
     │                                                    │
     │  ◄──────────────────────────────────────────────  │
     │  200|new_email.txt~mail_0001.txt                  │
     │                                                    │
     │  ④ LOGOUT                                         │
     ├───────────────────────────────────────────────────►│
     │  ◄──────────────────────────────────────────────  │
     │  200|Goodbye                                       │
```

---

## 3.2 Thiết kế giao thức ứng dụng

### 3.2.1 ⚠️ Vấn đề bắt buộc phải giải: Framing

UDP có 3 đặc điểm gây khó khăn cho bài này:

| Đặc điểm | Hậu quả với bài Mail Server |
|---|---|
| **Không có khái niệm "connection"** | Không biết "phiên" của client bắt đầu và kết thúc ở đâu → phải tự thêm `LOGIN`/`LOGOUT` |
| **Có ranh giới datagram, không có ranh giới trường** | Datagram tới nguyên vẹn, nhưng bên trong là chuỗi phẳng — không biết đâu là giữa `arg1` và `arg2` → **phải tự định nghĩa dấu phân tách trường** |
| **Không bảo đảm tin cậy** | Client không biết server đã xử lý xong chưa → **phải có request/response + timeout** |

> **Phân biệt quan trọng:** UDP *không* cắt nhỏ hay gộp message (điều TCP mới làm).
> Một `send()` của ta **luôn** tạo đúng 1 datagram, và `receive()` **luôn** trả về đủ
> datagram đó. Cái ta phải tự thiết kế chỉ là ranh giới **giữa các trường logic**
> (`|`), ranh giới **giữa các response** khi client xử lý nhiều response trên cùng
> socket, và cách **escape** khi dữ liệu chứa ký tự phân tách.

### 3.2.2 Phương án A — Text-based với ký tự phân tách (CHỌN)

#### Định dạng

```
REQUEST :  <OP>|<arg1>|<arg2>|...|<argN>|<CRLF>
RESPONSE:  <STATUS>|<message><CRLF>
```

- `<OP>` – mã lệnh (đã cân đối hóa để có thể so sánh bằng `==`)
- `<CRLF>` = `\r\n` — kết thúc request. Với UDP, `<CRLF>` **không bắt buộc về mặt kỹ thuật**
  (datagram đã tự định nghĩa ranh giới) nhưng ta vẫn thêm để: (a) client đọc dòng bằng
  `BufferedReader` được tự nhiên, (b) message có thể in ra log/terminal dễ đọc,
  (c) nếu sau này chuyển sang TCP thì protocol không phải thiết kế lại
- `<message>` trong response có thể chứa nhiều giá trị, phân tách bằng `~`

#### Bảng protocol đầy đủ

| OP | Request | Response (thành công) | Response (lỗi) |
|---|---|---|---|
| `REGISTER` | `REGISTER\|<user>\|<pass>` | `200\|Account <user> created` | `400\|Invalid username`<br>`409\|Account already exists`<br>`500\|Server error: ...` |
| `LOGIN` | `LOGIN\|<user>\|<pass>` | `200\|<f1>~<f2>~...~<fn>` | `401\|Invalid password`<br>`404\|Account not found` |
| `SEND` | `SEND\|<from>\|<to>\|<subject>\|<body>` | `200\|Delivered to <to>` | `400\|...`<br>`404\|Recipient not found` |
| `LOGOUT` | `LOGOUT` | `200\|Goodbye` | — |
| `LIST` *(mở rộng)* | `LIST\|<user>` | `200\|<f1>~<f2>~...~<fn>` | `400\|...`<br>`404\|Account not found` |
| `FETCH` *(mở rộng)* | `FETCH\|<user>\|<file>` | `200\|<nội dung thư đầy đủ>` | `400\|...`<br>`404\|File not found` |

Ba dòng cuối là **phần mở rộng tự thêm**, không thuộc đề bài. Ba lệnh của đề bài
(`REGISTER`, `LOGIN`, `SEND`) đều xác thực bằng mật khẩu; `LOGOUT` giữ nguyên nguyên tắc đó.
`LIST`/`FETCH` **không mang mật khẩu** — xem [§3.7.3](#373-hạn-chế-bảo-mật-của-phần-mở-rộng).

#### Bảng mã trả lời (lấy cảm hứng từ HTTP)

| Mã | Nhóm | Ý nghĩa |
|---|---|---|
| `200` | 2xx Thành công | OK |
| `400` | 4xx Lỗi | Bad Request — định dạng sai, username không hợp lệ |
| `401` | 4xx | Unauthorized — sai mật khẩu |
| `404` | 4xx | Not Found — không tìm thấy account |
| `409` | 4xx | Conflict — account đã tồn tại |
| `500` | 5xx | Internal Server Error — lỗi I/O phía server |

#### Ưu/nhược điểm

| | Đánh giá |
|---|---|
| ✅ | Dễ đọc khi debug (Wireshark, telnet, console) |
| ✅ | Giáo viên / người đọc code hiểu ngay |
| ✅ | Dễ in ra console để demo |
| ✅ | Không cần thư viện ngoài |
| ❌ | Phải **escape** ký tự `\|` trong dữ liệu người dùng |
| ❌ | Phải **escape** `\r`, `\n` trong body |
| ❌ | Không truyền được dữ liệu nhị phân |

### 3.2.3 Phương án B — Binary với length-prefix (nâng cao, không chọn)

#### Định dạng

```
┌──────────────┬────────────────────┬─────────┬──────────────────────────┐
│ 1 byte       │ 4 bytes (int)      │ 1 byte  │ N bytes                  │
│ OP           │ payload length     │ ERR     │ payload (UTF-8)          │
└──────────────┴────────────────────┴─────────┴──────────────────────────┘
     │                │                  │              │
     │                │                  │              └── Nội dung (tên, nội dung mail...)
     │                │                  └── Mã lỗi (0 = thành công)
     │                └── Số byte thực tế của payload → ĐỒNG Ý giải quyết framing
     └── Mã lệnh (byte đầu tiên)
```

#### Đánh giá

| | |
|---|---|
| ✅ | **Không bao giờ lỗi dữ liệu** — có length chính xác, không cần escape |
| ✅ | Truyền được dữ liệu nhị phân |
| ✅ | Có thể thêm CRC32 chống lỗi truyền |
| ❌ | Khó trình bày / giải thích cho giáo viên theo đề UDP cơ bản |
| ❌ | Debug khó hơn (phải hex dump) |

> 👉 **Quyết định:** Chọn **Phương án A** cho bài nộp. Nếu giáo viên hỏi "nếu làm chuyên sâu
> thì sao?", trả lời: sẽ chuyển sang length-prefix + checksum (§3.4.3).

### 3.2.4 Quy tắc escape bắt buộc (cho Phương án A)

| Ký tự trong dữ liệu | Ký tự gửi đi | Quy tắc |
|---|---|---|
| `\|` | `\\|` | Thêm dấu `\` trước |
| `\r` hoặc `\n` | `<BR>` | Thay thế (body là nội dung 1 dòng) |
| Ký tự `<` `>` | Giữ nguyên | Không cần escape |
| Ký tự điều khiển khác | Bỏ qua | Lọc bằng regex |

```java
// Escape khi gửi
static String escape(String s) {
    return s.replace("\\", "\\\\")
            .replace("|", "\\|")
            .replace("\r", "")
            .replace("\n", "<BR>");
}

// Unescape khi nhận
static String unescape(String s) {
    return s.replace("\\|", "|")
            .replace("<BR>", "\n");
}
```

### 3.2.5 Giới hạn kích thước (để không vượt 65507 byte)

| Hạng mục | Giới hạn |
|---|---|
| Username | 32 ký tự |
| Password | 64 ký tự |
| Subject | 200 ký tự |
| **Body** | **8 000 ký tự** |
| **Tổng request tối đa** | **< 60 000 byte** (an toàn dưới 65 507) |
| Buffer nhận phía server | `byte[65535]` |
| Buffer nhận phía client | `byte[65535]` |

---

## 3.3 Đặt tên file email

### 3.3.1 Vấn đề trùng tên

Nếu đặt tên file theo thời gian mà không có gì đảm bảo duy nhất:

```
mail_20261003_223012.txt   ← mail thứ 1 lúc 22:30:12
mail_20261003_223012.txt   ← mail thứ 2 cũng lúc 22:30:12  → GHI ĐÈ, mất mail!
```

### 3.3.2 Các phương án và đánh giá

| Phương án | Ví dụ | Ưu | Nhược |
|---|---|---|---|
| **Timestamp** | `mail_20261003_223012.txt` | Dễ hiểu, sắp xếp theo thời gian | ⚠️ Trùng nếu gửi 2 mail trong cùng giây |
| **Timestamp + số thứ tự** | `mail_20261003_223012_1.txt` | Không trùng | Dài |
| **⭐ Sequence đếm tăng** | `mail_0001.txt`, `mail_0002.txt` | **Không bao giờ trùng**, dễ đọc, dễ sort | Không tự mang thông tin thời gian (nhưng header đã có) |
| **Kiểu Maildir** | `1727957400.M123456P789.txt` | Không trùng tuyệt đối | Khó đọc |
| **UUID** | `a3f2b1c4-....txt` | Không trùng tuyệt đối | Khó đọc, dài |

### 3.3.3 ✅ Quyết định: sequence đếm tăng

```java
// Đếm số file mail_*.txt hiện có → tạo file kế tiếp
private String nextMailFileName(File mailbox) {
    int max = 0;
    File[] files = mailbox.listFiles();
    if (files != null) {
        for (File f : files) {
            String n = f.getName();
            if (n.startsWith("mail_") && n.endsWith(".txt")) {
                try {
                    max = Math.max(max, Integer.parseInt(n.substring(5, 9)));
                } catch (NumberFormatException ignored) { }
            }
        }
    }
    return String.format("mail_%04d.txt", max + 1);
}
```

### 3.3.4 ⚠️ Về file `new_email.txt`

Đề bài **ghi rõ tên `new_email.txt`** → phải giữ đúng tên này để giáo viên kiểm tra.

| File | Khi nào tạo | Nội dung |
|---|---|---|
| `new_email.txt` | 1 lần, lúc **đăng ký account** | Mail chào mừng (nội dung đúng yêu cầu đề) |
| `mail_0001.txt` | Mỗi lần gửi email | Nội dung email người dùng gửi |
| `mail_0002.txt` | … | … |

Lưu ý: `new_email.txt` không khớp mẫu `mail_*.txt` → hàm đếm sequence sẽ bỏ qua nó, không tính
nhầm.

---

## 3.4 Bảo mật – các lỗ hổng cần phòng chống

### 3.4.1 ⚠️ Path Traversal (lỗ hổng nghiêm trọng nhất)

**Tấn công:** Client gửi `REGISTER|../../etc|pass`

```
Nếu server ghép thẳng:  new File("mailserver_data", "../../etc")
→ Đường dẫn thực:  ../../etc
→ Server tạo/ghi file NGOÀI thư mục dữ liệu!
```

Hậu quả:
- Tạo/ghi đè file tùy ý trên máy chủ
- **Đọc** được file nhạy cảm (đặc biệt với lệnh `LOGIN` → `LIST`)
- Ghi đè `/etc/passwd` → chiếm quyền

**Phòng chống:**

```java
// ✅ Validate bằng regex — chỉ cho phép chữ, số, gạch dưới
private static final Pattern VALID_USERNAME =
        Pattern.compile("^[a-zA-Z0-9_]{3,32}$");

if (!VALID_USERNAME.matcher(username).matches()) {
    return "400|Invalid username (only 3-32 chars: a-z, A-Z, 0-9, _)";
}
```

### 3.4.2 Bảng tổng hợp các lỗ hổng

| Lỗ hổng | Mô tả | Mức | Cách phòng chống |
|---|---|---|---|
| **Path Traversal** | `../` trong username để thoát khỏi thư mục dữ liệu | 🔴 Cao | Regex `^[a-zA-Z0-9_]{3,32}$` |
| **Null byte injection** | `\0` trong tên để cắt chuỗi path | 🟡 TB | Lọc ký tự điều khiển (Java hiếm gặp) |
| **Overwrite / Data loss** | `REGISTER` 2 lần → ghi đè mail cũ | 🟡 TB | Kiểm tra đã tồn tại → trả `409` |
| **Password plaintext** | Lưu/gửi mật khẩu rõ | 🟡 TB | Trong phạm vi lab chấp nhận, nhưng **không gửi lại** password trong response |
| **Giả danh người gửi** | Client khai `from=bob` khi chưa đăng nhập | 🟡 TB | Yêu cầu `token` từ `LOGIN` (nâng cao) |
| **Tràn bộ đệm (buffer overflow)** | Message quá dài | 🟢 Thấp | `DatagramPacket` tự cắt an toàn + kiểm tra độ dài |
| **Từ chối dịch vụ (DoS)** | Gửi 1 triệu request | 🟡 TB | Giới hạn tần suất, timeout |
| **Thư mục khổng lồ** | Tạo 10000 account | 🟢 Thấp | (ngoài phạm vi lab) |

### 3.4.3 Nâng cao (nếu giáo viên hỏi "làm thế nào để bảo mật hơn?")

| Cải tiến | Mô tả |
|---|---|
| **Hash mật khẩu** | SHA-256 thay vì lưu rõ. `MessageDigest.getInstance("SHA-256")` — *(bài này đã dùng)* |
| **Salt + PBKDF2** | SHA-256 trần **không** chống được rainbow table; chuẩn là PBKDF2/bcrypt/Argon2 với salt ngẫu nhiên |
| **Token phiên** | `LOGIN` trả về token ngẫu nhiên, `SEND` phải kèm token |
| **Rate limiting** | Đếm số request/giây, từ chối nếu vượt |
| **Ghi log truy cập** | Log ai đăng nhập lúc mấy giờ, gửi mail cho ai |
| **Mã hóa kênh truyền** | Chuyển sang TCP + `SSLSocket` (TLS cho TCP). Với UDP thì phải dùng **DTLS** qua `SSLEngine` (`DatagramFlowContext`/`SSLEngine`), *không* bọc được bằng `DatagramSocket` thuần |
| **CRC32 + length prefix** | Phát hiện lỗi mạnh hơn checksum 16 bit của UDP (§3.2.3 phương án B) |

---

## 3.5 Xử lý đồng thời trên server

### 3.5.1 Ba cách tổ chức

#### Cách 1 — Single-thread (đơn giản nhất)

```
while (true) {
    receive(pkt);        // chặn
    String resp = xuLy(pkt);   // xử lý tuần tự
    send(pkt);           // trả lời
}
```

| ✅ | ❌ |
|---|---|
| Code ngắn gọn, dễ hiểu | 1 client gửi mail dài → **tất cả client khác bị nghẽn** |
| Không lo tranh chấp | Không tận dụng đa nhân |

#### Cách 2 — Thread per request (có bẫy)

Mỗi request tạo 1 thread → nhưng **nếu dùng chung `DatagramSocket`** sẽ có **race condition**:
Thread A gọi `receive()` nhưng Thread B "cướp" mất gói tin.

| ⚠️ Bẫy | Hậu quả |
|---|---|
| Dùng chung `DatagramSocket` cho nhiều thread | Hai thread có thể nhận cùng 1 datagram, thread này gửi reply về địa chỉ của thread kia |
| Thiếu `synchronized` | Reply tới đúng địa chỉ ngẫu nhiên |

#### Cách 3 — ⭐ Listener thread + Thread pool (KHUYẾN NGHỊ)

```
                    ┌──────────────────────────┐
                    │  Thread "listener"       │
UDP port 2346 ──────►│  while(true) receive()   │
                    └────────────┬─────────────┘
                                 │ nhận được pkt
                                 ▼
                    ┌──────────────────────────┐
                    │  ExecutorService         │
                    │  (thread pool, 10 thread) │
                    │  → xử lý file (I/O)      │
                    └────────────┬─────────────┘
                                 │ mỗi thread tạo DatagramSocket RIÊNG để gửi reply
                                 ▼
                          send reply về pkt.getAddress():pkt.getPort()
```

| ✅ Ưu điểm |
|---|
| Nhiều client dùng đồng thời mà **không nghẽn** |
| Listener không bị chặn bởi I/O file |
| **Không có race condition** (mỗi thread một socket riêng để gửi) |
| Giới hạn số thread → không tràn tài nguyên |

---

## 3.6 Lập trình giao diện Swing: EDT và bẫy luồng

Máy chủ và máy khách đều chạy trên Swing. Phần này nói về điều dễ sai nhất khi ghép socket
với giao diện đồ hoạ.

### 3.6.1 Vì sao không được gọi socket trên EDT

Swing có một luồng riêng gọi là **Event Dispatch Thread (EDT)** lo gần như mọi thao tác với giao diện:
tạo component, vẽ, xử lý sự kiện chuột/bàn phím. Yêu cầu của EDT là **phải trả về nhanh** —
nó là luồng duy nhất được phép chạm vào cây component.

Còn `DatagramSocket.receive()` là lệnh **chặn**: nó đứng yên cho tới khi có datagram về, tối đa
`setSoTimeout()` mili giây. Nếu gọi trên EDT thì trong suốt thời gian đó Swing không vẽ lại,
không nhận sự kiện, không đóng được cửa sổ. Cửa sổ trông như bị treo.

| Lệnh | Chặn tối đa | Gọi trên EDT |
|---|---|---|
| `socket.receive()` | `soTimeout` (3000 ms) | Treo giao diện 3 giây |
| `new FileWriter(...).write(...)` | tuỳ độ lớn file | Treo giao diện |
| `new MailServer(...).start()` | không chặn (thread riêng) | Chấp nhận được |

### 3.6.2 `SwingWorker`: đúng và sai

```java
// SAI — treo giao diện
button.addActionListener(e -> {
    String[] r = client.login(user, pass);   // chặn 3 giây trên EDT
    resultLabel.setText(r[1]);
});

// ĐÚNG — I/O ở luồng nền, cập nhật UI ở done()
button.addActionListener(e -> new SwingWorker<String[], Void>() {
    @Override protected String[] doInBackground() {   // luồng nền
        return client.login(user, pass);
    }
    @Override protected void done() {                // lại EDT
        try {
            setResult(resultLabel, get()[0], get()[1]);
        } catch (Exception ex) {
            showError(resultLabel, ex);
        } finally {
            setBusy(false);        // mở khoá giao diện, kể cả khi lỗi
        }
    }
}.execute());
```

Ba điểm dễ quên:

1. **`done()` phải mở khoá trong `finally`.** Nếu chỉ mở khoá trong nhánh thành công, một
   lần `get()` ném `ExecutionException` sẽ để cờ `busy = true` mãi mãi và toàn bộ nút bị khoá vĩnh viễn.
2. **`done()` có thể chạy cả khi `doInBackground()` ném lỗi**, nên phải bọc `get()` trong `try/catch`.
3. **`get()` chặn.** Gọi nó trong `doInBackground()` là vô nghĩa; chỉ gọi trong `done()`.

### 3.6.3 `synchronized` trên `request()`

Client giữ **một** `DatagramSocket` cho cả phiên. `request()` gồm hai bước: gửi, rồi nhận.
Nếu hai luồng cùng chạy:

```
Luồng A: send() ─────────────────────────►
Luồng B:              send() ─────────────────►
Luồng B:              receive() ◄── nhận response của A   ← SAI
Luồng A:              receive() ◄── nhận response của B   ← SAI
```

Hai người dùng nhận lẫn response của nhau mà **không có lỗi nào được ném** — UDP không mang
định danh request, nên máy không thể biết gói tin trả về là của ai. Đây là loại lỗi nguy hiểm
nhất: chạy vẫn "đúng", chỉ là sai người.

Vì vậy `request()` phải là `synchronized`. Ở tầng GUI còn khoá thêm cờ `busy` để người dùng
không bấm hai lần liên tiếp — cờ này không thay thế `synchronized`, chỉ là lớp bảo vệ thứ hai cho
trải nghiệm người dùng.

### 3.6.4 Đưa dữ liệu từ luồng nền sang EDT

Nhật ký của server sinh ra trên luồng listener, không phải EDT. Gọi `logArea.append()` từ đó là
vi phạm quy tắc luồng của Swing (lỗi này hiếm khi lộ ra ngay, thường chỉ biểu hiện ở những lần
repaint lẻ tẻ nên rất khó tìm). Hai cách đúng:

```java
// Cách 1: invokeLater trực tiếp
SwingUtilities.invokeLater(() -> logArea.append(line + "\n"));

// Cách 2: đẩy vào hàng đợi rồi flush một lần trên EDT (dùng trong MailServerFrame)
private final Queue<String> pending = new ConcurrentLinkedDeque<>();

private void appendLogLine(String line) {
    pending.add(line);
    SwingUtilities.invokeLater(this::flushLog);   // nhiều dòng vào đều chỉ flush 1 lần
}

private void flushLog() {
    StringBuilder sb = new StringBuilder();
    String line;
    while ((line = pending.poll()) != null) sb.append(line).append('\n');
    if (sb.length() == 0) return;
    logArea.append(sb.toString());
    logArea.setCaretPosition(logArea.getDocument().getLength());
    // giới hạn bộ nhớ: xoá đầu khi vượt MAX_LOG_LINES
}
```

Cách 2 tốt hơn khi server bị dội request: 100 dòng log đến cùng lúc sẽ chỉ gây **một** lần
repaint thay vì 100 lần.

### 3.6.5 Look & Feel và `UIManager`

Swing lấy màu và font mặc định từ Look & Feel đang cài. Ghi đè bằng `UIManager.put()` chỉ có tác
dụng với những key mà LAF đó thực sự đọc — ví dụ Nimbus **không** dùng
`Viewport.background`, nên vùng văn bản cuộn vẫn hiện màu trắng `#FDFDFC` của Nimbus dù đã đặt
key trong `UIManager`.

Hai bài học rút ra từ đúng lỗi này:

- Với thành phần lõi như `JScrollPane`/`JViewport`, **tắt opacity** (`setOpaque(false)`) để lộ
  nền của panel cha, thay vì cố ghi đè key LAF. Cách này độc lập LAF.
- Với thành phần tự vẽ như `FlatButton`, phải tự lo toàn bộ: hover, focus ring, trạng thái
  disabled. `JComponent` không tự cho mình bất cứ thứ gì.

Ngoài ra `UIManager` chỉ có hiệu lực **trước** khi component được tạo. Vì vậy
`Theme.apply()` phải chạy ở đầu constructor, không đặt trong `main()` — nếu ai đó khởi tạo
`new MailClientFrame()` từ chỗ khác (ví dụ một ứng dụng nhiều cửa sổ) thì giao diện sẽ rơi về
màu và font mặc định của LAF.

### 3.6.6 `getBorderInsets` và chiều cao ô nhập

Một lỗi rất dễ bỏ sót: `AbstractBorder` mặc định trả về `Insets` bằng 0. Nếu một `Border`
tự vẽ mà không override `getBorderInsets`, ô nhập sẽ:

1. không có khoảng đệm — chữ dính sát mép viền;
2. có `preferredSize.height` chỉ bằng chiều cao dòng chữ (~16 px) thay vì ~30 px.

Trong `GridBagLayout`, component được đặt đúng bằng preferred size, nên ô nhập bị bẹo lại 16 px
trông rất lạ. Không thể sửa bằng cách đặt `setPreferredSize` ở nơi khác tùy ý — phải khai báo
padding trong chính `Border`.

### 3.6.7 Nút không phải `JButton`

`FlatButton` mở rộng `JComponent`, không phải `JButton`, để toàn bộ hình dáng do mình vẽ.
Hai hệ quả phải xử lý tay, nếu bỏ sót thì giao diện "nhìn được nhưng dùng không nổi":

| Vấn đề | Cách sửa |
|---|---|
| Người dùng bàn phím không bấm được | `setFocusable(true)` + key binding cho `Space`/`Enter` |
| Không thấy nút nào đang focus | Vẽ focus ring trong `paintComponent` |
| Nút bị khoá trông y hệt nút đang bật | Tô nhạt nền và chữ, đổi con trỏ chuột |
| Bấm xuống ở nút rồi kéo ra ngoài thả ra vẫn kích hoạt | Chỉ kích hoạt khi thả chuột **bên trong** nút |

### 3.6.8 ⚠️ Chữ không hiện dù kích thước đúng

Cùng bản chất với 3.6.7 nhưng nguy hiểm hơn: **component có kích thước đúng, có trong cây,
lại không vẽ ra chữ**. Nút phẳng của Swing mặc định vẽ nền *sau khi* vẽ chữ, nên một đường bo
góc tô đè lên chữ là toàn bộ chữ biến mất mà không có gì báo lỗi.

Nguyên tắc đã áp dụng trong `Theme`:

| Sai lầm | Cách sửa |
|---|---|
| `paintComponent` tô nền, rồi gọi `super.paintComponent` | `super.paintComponent(g)` **trước**, rồi mới tô nền phủ lên — nhưng phải vẽ chữ riêng sau nền |
| `paintBorder` tô nền | `paintBorder` chỉ tô **nét viền**, tuyệt đối không tô nền |
| Dùng `JButton` mặc định rồi thay `border` | Tự vẽ: nền trong `paintComponent`, chữ sau cùng |

Vì vậy bộ kiểm thử đo **pixel thật** chứ không đo kích thước: vẽ riêng từng component vào
ảnh, đếm số pixel khác màu nền ở phần nội (bỏ 3px sát viền). 0 pixel ⇒ chữ không hiện.
Chạy cả trước và sau đăng nhập, vì trước đăng nhập các tab chưa dùng tới đang **ẩn**.

---

## 3.7 Hai mở rộng tự thêm: `LIST`, `FETCH` (và vòng poll)

### 3.7.1 Vì sao cần thêm

Đề bài yêu cầu `LOGIN` trả về **tên file**. Nhưng nếu chỉ có vậy thì:

1. Không đọc được nội dung thư — biết có file `mail_0001.txt` mà không biết trong đó có gì.
2. Không có cách nào biết thư mới tới lúc nào — client đã đăng nhập từ trước, mọi thư gửi
   tới sau đó nó đều không biết. Người dùng phải đăng xuất rồi đăng nhập lại mới thấy.

Hai lệnh `LIST` và `FETCH` được thêm để sửa đúng hai khoảng trống đó. Cả hai là **tiện ích
mở rộng, không phải yêu cầu đề bài**.

### 3.7.2 Realtime bằng cách poll, không phải push

UDP không có kết nối, không có kênh đi, không có cơ chế push. Nên "nhận thư realtime" ở đây
đơn giản và trung thực là **poll**: sau khi đăng nhập, client gửi `LIST|<user>` mỗi **1 giây**.

| Quyết định | Giá trị | Lý do |
|---|---|---|
| Chu kỳ | 1000 ms | 1 giây là chu kỳ chuẩn của bài Lab 5 Bài 1 (`ExchangeRate`); nhanh hơn chỉ tốn CPU vô ích, chậm hơn thì "realtime" thành "chậm" |
| Timeout mỗi lần poll | 800 ms (thay vì 3000 ms) | Poll là việc **nền**; không nên để người dùng chờ 3 giây khi mạng chậm. Một vòng timeout thì bỏ qua vòng sau |
| Thread | daemon, `ScheduledExecutorService` | Daemon để đóng cửa sổ không bị treo bởi executor |
| Cập nhật model | chỉ khi `List.equals` cho thấy khác | Không dựng lại `JList` mỗi giây — sẽ nhảy, mất vị trí chọn, tốn CPU |
| Đánh dấu chưa đọc | `●` chỉ cho tên **mới xuất hiện** | Người dùng cần biết cái nào mới; xoá `●` khi bấm xem |

Ba lỗi dễ gặp khi viết vòng poll, đều đã xử lý:

- **Không dừng poll trước khi đóng socket.** Vòng đang chạy sẽ thấy `client == null` giữa
  chừng và đụng vào socket đã đóng. → `stopPolling()` phải chạy **trước** `close()`.
- **Để poll chồng với thao tác của người dùng.** Hai request trên cùng một socket sẽ lệch
  phản hồi. → `request()` `synchronized` + kiểm tra `busy` ở đầu mỗi vòng.
- **Thread pool của Swing không dừng lại khi đóng cửa sổ.** → dùng thread daemon riêng cho
  poll, tách khỏi pool chung.

### 3.7.3 Hạn chế bảo mật của phần mở rộng

Nói thẳng, vì đây là điểm giáo viên hỏi nhiều nhất:

| Lệnh | Có mật khẩu? | Hậu quả |
|---|---|---|
| `REGISTER` / `LOGIN` / `SEND` | ✅ Có | Đúng yêu cầu đề bài |
| `LOGOUT` | — (không cần) | Chỉ là xác nhận ở tầng protocol |
| `LIST` / `FETCH` | ❌ **Không** | Ai gõ `FETCH\|alice\|new_email.txt` cũng đọc được thư của `alice` |

**Vì sao chọn vậy:** giữ nguyên tắc *"lệnh nào sinh ra thì phải xác thực"* — `LIST` và `FETCH`
không phải yêu cầu đề bài nên không bắt buộc xác thực. Làm nửa vời (kiểm tra "đã đăng nhập
chưa" trong bộ nhớ server) sẽ **vừa không an toàn vừa sai**: UDP không có phiên nên cái
"đã đăng nhập" đó chỉ là biến trong RAM, không chứng minh được điều gì trước người lạ.

**Hệ quả thứ hai, ít người để ý:** vì `SEND` không mang phiên và server không lưu phiên,
`LOGOUT` chỉ có tác dụng **phía client**. Nếu không bấm `Đăng xuất`, client vẫn coi là còn
phiên và poll tiếp. Muốn làm đúng chuẩn thì phải:

| Cần gì | Vì sao |
|---|---|
| `SEND` mang mật khẩu (hoặc token) | Không ai gửi được thư bằng tên người khác chỉ cần biết account tồn tại |
| `LIST`/`FETCH` mang mật khẩu | Chặn việc đọc trộm thư |
| `LOGIN` trả token có hạn, `LOGOUT` thu hồi token | Thực sự có phiên để hủy |
| `accounts.dat` đổi sang PBKDF2/bcrypt/Argon2 có salt | SHA-256 không salt chống được rainbow table |

**Hệ quả thứ ba, liên quan tới `Receiver-IP` ([§3.7.6](#376-lưu-ip-người-gửi-và-ip-người-nhận)):**
vì `FETCH` không xác thực nên `Receiver-IP` chỉ chứng minh được *"máy nào đã mở thư **trước
tiên**"*, **không** chứng minh được *"máy nào là chủ sở hữu thư"*. Hai máy cùng biết mật khẩu
`tuan` đều có thể là người đọc hợp lệ; và vì request không có mật khẩu, một máy lạ cũng đọc
được thư rồi ghi đè vị trí "đọc đầu tiên". Khi trình bày, phải nói rõ đây là **số liệu vận
hành để thống kê, không phải cơ chế xác thực danh tính** — cùng hạn chế với chính phần
`LIST`/`FETCH` nói ở trên.

### 3.7.4 Giao diện chỉ mở những gì dùng được

Trước khi đăng nhập chỉ hiện `Đăng ký` + `Đăng nhập`; thẻ `Hộp thư`, tab `Gửi thư`, tab
`Đọc thư` và nút `Đăng xuất` đều **ẩn**. Sau khi đăng nhập thì mới hiện đủ và bật poll.

Lý do phải làm vậy: bấm được một nút mà không kết nối là điều hướng người dùng vào
nhánh lỗi. Còn khi đã đăng nhập mà vẫn thấy đầy đủ thì dễ quên mình còn đang dùng tài
khoản nào. Sau `Đăng xuất`, UI trở về đúng trạng thái ban đầu — kết nối UDP vẫn giữ nên
đăng nhập lại không cần bấm `Kết nối`.

Lưu ý kỹ thuật: ẩn **cả hàng** chứa nút `Đăng xuất` chứ không chỉ ẩn nút. `FlowLayout` không
giữ chỗ cho component con đã ẩn, và ẩn con trong một hàng 3 nút (~290px) trong card rộng 244px
sẽ làm nút xuống hàng — nhìn rối. Giải pháp dùng ở đây là **hai hàng cố ý**:
hàng 1 là `Kết nối`/`Ngắt`, hàng 2 là `Đăng xuất`.

Và cần biết giới hạn của công cụ kiểm thử: `GeoCheck` phát hiện component **chồng lấn**, còn
`FlowLayout` làm nút **xuống dòng** thì không chồng lấn gì cả — công cụ không bắt được. Loại lỗi
đó chỉ thấy được bằng cách nhìn tọa độ `y` của từng nút. Nói chung: **một công cụ kiểm tra chỉ
bắt được loại lỗi mà nó được thiết kế để bắt**, nên phải có nhiều công cụ bổ trợ nhau
(`E2E` cho hành vi, `InkCheck` cho chữ, `GeoCheck` cho hình học) chứ không nên trông chờ một
công cụ duy nhất.

### 3.7.5 Bài học từ lỗi thật: "gửi cho chính mình thì không xem được thư"

Kịch bản người dùng báo: *đã gửi thư cho chính mình mà không xem được thư đó*. Khi viết lại
kịch bản này thành test để tái hiện, hoá ra phần lõi vốn đã chạy đúng — và phát hiện **ba lỗi
thật** ẩn quanh nó. Cả ba đều là lỗi *trạng thái*, không phải lỗi gửi/nhận.

#### Lỗi 1 — thư vừa gửi bị đánh dấu "chưa đọc" và nhảy chỗ sau 1 giây

Khi `SEND` thành công, GUI thêm tên file vào danh sách hiển thị. Nhưng **danh sách mà vòng poll
so sánh** (`MailClient.currentMailList`) thì chưa được cập nhật. Hậu quả kép:

- Vòng poll kế tiếp thấy `after != before` → tưởng có **thư mới** → gắn dấu `●` chưa đọc
  cho đúng thứ người dùng vừa tự viết.
- Danh sách hiển thị thêm file ở **cuối**, còn server trả về danh sách đã
  `Collections.sort` → sau một giây thư **nhảy chỗ** trên màn hình.

Sửa: `MailClient.noteDelivered()` cập nhật danh sách ngay khi gửi thành công, và **sắp xếp
y hệt cách server sắp xếp**. Chỉ cần hai bên cùng một thứ tự là `before == after`, vòng poll im
lặng, mọi triệu chứng biến mất.

> Bài học: khi có hai bản sao cùng một dữ liệu (bản hiển thị và bản dùng để so sánh), phải cập
> nhật **cả hai** trong cùng một chỗ. Sửa một bản là bug chờ đổi tên.

#### Lỗi 2 — `refreshMailbox()` âm thầm tải lại thư đang đọc

Để giữ đúng lựa chọn khi danh sách thay đổi, `refreshMailbox()` làm `clear()` rồi `addElement()`
rồi `setSelectedIndex()` lại thư đang xem. Nhưng `clear()` làm mất chọn và `setSelectedIndex()`
chọn lại — **`ListSelectionListener` bị kích hoạt**, tức là bị hiểu là người dùng bấm thư. Kết
quả: **mỗi lần có thư mới, app tải lại thư đang đọc**, tức là một vòng lặp vô hạn nếu người dùng
cứ để app mở một thư.

Sửa bằng cờ `restoringSelection`: khi chính ta đang sửa danh sách thì listener bỏ qua, còn khi
người dùng thật sự bấm thì vẫn xử lý bình thường.

> Đây là lớp lỗi rất đáng nhớ: **code sửa mô hình không được giả vờ thành thao tác của người
> dùng**. Test cũng vì thế mới phải so *đếm request thật*, chứ không chỉ so giao diện.

#### Lỗi 3 — bấm nút lúc đang xử lý thì im lặng

Mọi thao tác đều mở đầu bằng `if (!requireClient(x) || !setBusy(true)) return;`. Khi đang xử lý,
`setBusy(true)` trả `false` và hàm **thoát im lặng** — người dùng bấm "Đọc thư" không thấy gì hết
rồi tưởng app treo. Sửa bằng `begin(JLabel target)` gom hai kiểm tra lại và **báo rõ**:
*"Đang xử lý yêu cầu trước, thử lại sau."*

#### Vì sao phải viết test cho kịch bản "người dùng thật"

Hồi đầu, bộ test chỉ gửi thư **từ máy khác** sang cho tài khoản đang mở GUI. Nhánh `SEND`
thành công tới chính người gửi — tức là `toField = tên chính mình` — lại không được ai bấm. Ba
lỗi trên nằm ngay trên nhánh đó. Bài học: **test theo đường đi của người dùng, không theo đường
đi của mình nghĩ code sẽ chạy.**

Ngoài ra, cần phân biệt rõ lỗi của app và lỗi của nền tảng. Máy tác giả gặp
`ArrayIndexOutOfBoundsException` trong `sun.awt.X11InputMethodBase` khi gõ tiếng Việt — stack
toàn bộ nằm trong JDK, không có frame nào của bài. Đó là lỗi input method của JDK trên X11, và
cách xử lý đúng là đổi JRE hoặc đổi bộ gõ, **không phải sửa code bài**.

### 3.7.6 Lưu IP người gửi và IP người nhận

Bài Lab 5 mở rộng để ghi thêm **hai dòng header** vào mỗi file thư, rồi hiện ra ở tab
`Đọc thư`:

| Dòng header | Ghi vào lúc nào | Lấy IP từ đâu | Ghi tối đa |
|---|---|---|---|
| `Sender-IP` | khi nhận lệnh `SEND` | `DatagramPacket.getAddress()` của chính request đó | 1 lần |
| `Receiver-IP` | khi `FETCH` **lần đầu** | `DatagramPacket.getAddress()` của chính request `FETCH` đó | 1 lần |

File thư sau khi gửi và sau khi đọc lần đầu:

```
From: minh@mailserver.local
To: hung@mailserver.local
Subject: Bao cao tuan 5
Date: Mon, 05 Oct 2026 16:08:06 +0700
Sender-IP: 172.16.0.252
Message-ID: <1791191286.cfe5a@mailserver.local>
MIME-Version: 1.0
Content-Type: text/plain; charset="UTF-8"
Receiver-IP: 192.168.1.55

Bao cao tuan 5 da nop.
```

**Bài học lý thuyết quan trọng nhất: vì sao `Receiver-IP` phải ghi lúc ĐỌC chứ không lúc GỬI**

Khi máy chủ giao thư cho tài khoản `hung`, thứ nó biết được là **tài khoản** `hung` — còn máy
nào sẽ mở thư đó thì **hoàn toàn không biết**. Máy chủ không định tuyến thư (không có bảng
định tuyến tới từng máy), không có bảng ARP riêng, và nhiều máy có thể cùng đăng nhập một
tài khoản. Vậy nên:

- Ghi lúc gửi → không có IP để ghi, hoặc ghi bừa tên miền `hung` (vô nghĩa với IP).
- Ghi lúc đọc lần đầu → **có IP thật**, và quy định "chỉ ghi một lần, không ghi đè" để
  dòng này giữ được ý nghĩa *"người nhận đầu tiên"*, không bị đổi mỗi lần thư được mở.

Đây là hiện tượng giống hệt trong thực tế: header `Received` của email bị chèn **mỗi bước
chuyển tiếp** (hop) chứ không phải lúc tác giả bấm Gửi, và giá trị mà ta hay nhìn nhất
(`Received: from ... by ... with ...`) chính là của **hop cuối cùng** — tức bước chuyển tiếp
gần người nhận nhất. Chi tiết này được nêu ở [§2.2.2](#222-phiên-smtp-đầy-đủ-transcript).

**Ba chi tiết kỹ thuật đã dùng trong code**

1. **Chèn header phải tính từ vị trí dòng trống, không phải `+` vào đầu file.** Nếu chèn
   sai chỗ, dòng IP sẽ rơi xuống dưới dòng trống và **bị hiển thị như một đoạn văn của thư** —
   người đọc thấy "Sender-IP: 172.16.0.252" nằm giữa nội dung. Vì vậy `insertHeader()` tìm
   `\n\n` rồi chèn **ngay trước** nó.
2. **Ghi file phải an toàn như lúc giao thư.** `readMailWithReceiverIp()` cũng là
   `synchronized` và cũng ghi ra file tạm rồi `Files.move()`. Nếu chỉ `Files.writeString()`
   thẳng vào file thật mà máy bị tắt đột ngột giữa chừng, người dùng mất thư — một thao tác
   **đọc** lại làm hỏng dữ liệu thì còn tệ hơn lỗi gửi thư.
3. **`FETCH` phải trả về nội dung *đã cập nhật*.** Nếu server cập nhật file nhưng response
   vẫn là bản cũ thì client không thấy dòng IP cho tới lần đọc kế tiếp — và với chính tác
   giả lần đọc đầu tiên thì dòng đó phải xuất hiện ngay.

**Bài học đắt giá nhất của phần này: thêm một hàng vào layout là phải sửa cả các số thứ tự phía sau**

Thêm hai dòng IP vào giữa bảng header đã làm hỏng tab `Đọc thư` theo cách mà nhìn bằng mắt
rất khó phát hiện. Vì sao:

| Mã cũ | Chuyện gì xảy ra khi thêm 2 dòng header |
|---|---|
| header: dòng `1, 2, 3, 4` | — |
| thân thư: `g.gridy = 5` | trùng với dòng IP thứ nhất |
| nút: `g.gridy = 6` | trùng với dòng IP thứ hai |
| kết quả: `g.gridy = 7` | lệch xuống một hàng trống |

`GridBagLayout` cho phép nhiều component cùng nằm trong **một ô** `(gridx, gridy)`, và khi đó nó
**chia đôi chiều cao ô đó cho các component** chứ không xếp chồng. Nên không có gì
"giao nhau" theo nghĩa hình học — dấu hiệu chỉ là:

- hai dòng IP bị **đẩy xuống rất thấp** (đo được `y = 308` và `y = 517`, thay vì `129` và `150` ngay dưới dòng `Ngày`),
- vùng nội dung thư bị **bóp còn một nửa** chiều cao.

Cách sửa đúng không phải sửa hai con số, mà là **bỏ hẳn con số ma**: cho `addHeaderRow()` nhận
chỉ số hàng và một biến `row` tăng dần, rồi lấy `row`, `row + 1`, `row + 2` cho thân thư, nút
và dòng kết quả. Từ đó thêm bao nhiêu dòng header cũng không còn chuyện phải nhớ cập nhật.

**Và bài học về bộ kiểm thử — nó đã "xanh" một cách vô nghĩa.** `GeoCheck` và `InkCheck` kết luận
`HINH HOC SAN` / `MOI CHU DEU THUC SU HIEN` ngay cả khi lỗi còn nguyên, vì cả hai chỉ **đăng nhập
rồi đứng ở tab hộp thư**. Tab `Đọc thư` dùng `CardLayout` nên chỉ được bố trí (`validate`) khi
thực sự hiện ra; component của nó có toạ độ `(0, 0)` và `isShowing() == false` nên bộ dò quét
bỏ qua toàn bộ. Bài học chung:

> Một phép kiểm chỉ có giá trị bằng **trạng thái** mà nó kiểm. Muốn kiểm một tab thì phải **mở
> tab đó lên**, và phải chờ vòng poll đưa dữ liệu vào (dùng `waitUntil` thay vì `Thread.sleep`
> cố định, vì nếu không có đủ thư thì danh sách rông và bước mở thư sẽ ném `NullPointerException`).

Sau khi sửa, `GeoCheck` đăng nhập → gửi thư → **chờ thư xuất hiện trong hộp thư** → mở thư →
mới đo cả 6 dòng header, và `InkCheck` bấm `doClick()` vào nút tab `Đọc thư` để chữ thật sự
được vẽ ra.

**Hai điểm phải nói thẳng khi bảo vệ bài**

| Điểm | Giải thích |
|---|---|
| `Sender-IP` là dữ liệu tự khai | Máy gửi không tự chứng minh được IP của nó. Chỉ máy chủ mới biết chắc, vì IP đến từ tầng mạng chứ không từ nội dung request. Ta vẫn ghi đúng IP lấy từ `DatagramPacket` — **không** cho client tự khai trong phần body. |
| `Receiver-IP` không chống được giả mạo | `FETCH` không xác thực (xem [§3.7.3](#373-hạn-chế-bảo-mật-của-phần-mở-rộng)), nên nó chỉ là số liệu thống kê, không phải danh tính đã xác minh. |

Với hệ thống email thật, IP người gửi phải đến từ **tầng mạng** chứ không tin vào dữ liệu
người dùng gửi — cùng nguyên tắc với [§2.6.2](#262--chống-giả-mạo-spf-dkim-dmarc): SPF/DKIM
chính là để chứng minh *"tôi là tôi"* bằng bằng chứng từ bên thứ ba, chứ không phải bằng một
trường tự khai trong chính thư.

---

# PHẦN 4 — TỔNG HỢP

## 4.1 Bảng đối chiếu hệ thống thật ↔ bài Lab

| Khái niệm email thật | Giao thức / Chuẩn | Cổng | Bản mô phỏng trong bài Lab 5 |
|---|---|---|---|
| Gửi mail (người dùng → server) | `MAIL FROM` / `RCPT TO` / `DATA` | 587 | `SEND\|<from>\|<to>\|<subject>\|<body>` |
| Nhận mail (server → người dùng) | `DATA` + `.` | 25 | File `mail_0001.txt` trong mailbox |
| Tạo hộp thư | — | — | `mkdir data/<user>` |
| Mail chào mừng | — | — | File `new_email.txt` |
| Xác thực | `USER` / `PASS` (POP3), `LOGIN` (IMAP) | 110/143 | `LOGIN\|<user>\|<pass>` |
| Liệt kê mail | `LIST` (POP3), `SELECT` (IMAP) | 110/143 | `LOGIN` trả `f1~f2~..~fn`; thêm lệnh `LIST\|<user>` để poll |
| Đọc nội dung thư | `RETR` (POP3), `FETCH ... BODY[]` (IMAP) | 110/143 | Thêm lệnh `FETCH\|<user>\|<file>` |
| Định dạng message | RFC 5322 | — | Header `From/To/Subject/Date` trong file |
| Đặt tên file lưu trữ | Maildir | — | `mail_0001.txt` |
| Ranh giới message | Dấu `.` đơn lẻ | — | `\r\n` cuối message |
| Tra cứu đích | DNS MX | 53 | Client gửi tên account trong request |
| Mã trạng thái | `250` / `550` / `354` | — | `200` / `404` / `400` |
| Báo có thư mới (push) | Không có trong POP3/IMAP cũ | — | Client **poll `LIST` mỗi 1 giây** — xem §3.7.2 |

## 4.2 Câu hỏi thường gặp khi bảo về bài

### ❓ "Sao không dùng TCP mà dùng UDP?"

Xem §2.6.3. Tóm tắt: Email là ứng dụng mà **sai sót rất tệ** (mất mail = mất thông tin), nên
bắt buộc TCP. Đề bài dùng UDP **để luyện kỹ năng lập trình socket tầng transport** — đúng phạm
vi môn học — chứ không phải để chứng minh UDP là lựa chọn đúng cho email. Và chính vì UDP không
bảo đảm tin cậy, bài này buộc phải tự thiết kế protocol text có dấu phân tách, cơ chế timeout
và cách xử lý đồng thời.

### ❓ "UDP không có ranh giới message thì làm sao biết đã nhận đủ?"

Câu hỏi này có **tiền đề sai** — cần sửa lại:

- UDP **có** ranh giới message: trường `Length` trong header cho biết datagram dài bao nhiêu,
  nên `DatagramSocket.receive()` luôn trả về **trọn vẹn** 1 datagram. Không có chuyện
  "nhận được nửa request".
- Cái UDP **không** có là **ranh giới giữa các trường logic** bên trong datagram. Datagram là
  chuỗi byte phẳng; ta phải tự quy ước `|` là dấu tách trường và `\r\n` là dấu kết thúc
  request (để đọc dòng cho tiện và tương thích TCP).

Ba lớp phòng vệ cho phần *nội dung có cấu trúc*:
1. **Định dạng có dấu phân tách** — `|` tách trường, `\|` escape, `<BR>` = xuống dòng (§3.2.2)
2. **Giới hạn kích thước** — mọi trường có trần, tổng request ≤ 60 000 byte (an toàn dưới 65 507) (§3.2.5)
3. **Kiểm tra sau khi parse** — nếu thiếu/thừa số trường → trả `400 Bad Request`

### ❓ "Nếu datagram bị mất thì sao?"

Client đặt `setSoTimeout(3000)`. Hết 3 giây không có reply → in cảnh báo "Không phản hồi từ
server" và cho người dùng thử lại. Đây là giới hạn cố hữu của UDP mà bài tập muốn em nhận ra.

### ❓ "Làm sao chống client tạo account trùng?"

Kiểm tra `new File(dataDir, username).exists()` trước khi `mkdir`. Nếu đã tồn tại → trả
`409 Conflict`, **không** ghi đè (để không mất mail cũ).

### ❓ "Làm sao biết email gửi đến account nào?"

Từ trường `To:` trong nội dung email theo chuẩn RFC 5322 (thiết kế §3.2.2). Server dùng
chính trường này để quyết định ghi file vào thư mục nào — **giống hệt cách MTA thật dùng lệnh
`RCPT TO`**.

### ❓ "Server có thể phục vụ nhiều client cùng lúc không?"

Có. Dùng mô hình **listener thread + thread pool** (§3.5.1 cách 3). Mỗi worker tạo
`DatagramSocket` riêng để gửi reply → không race condition.

### ❓ "Password lưu thế nào cho an toàn?"

Trong phạm vi lab: so sánh trực tiếp trong RAM hoặc file, **không gửi lại** password trong
response. Nếu nâng cao: dùng `MessageDigest.getInstance("SHA-256")` để hash (§3.4.3).

### ❓ "Khác gì giữa TCP và UDP một cách ngắn gọn?"

| | UDP | TCP |
|---|---|---|
| Kết nối | Không | Có (handshake) |
| Tin cậy | Không | Có |
| Nhanh | Có | Chậm hơn |
| Dùng cho | DNS, game, video call | Web, email, file |

---

## TÀI LIỆU THAM KHẢO

### Bài giảng môn học (thư mục `LTM/`)
- `Bài 1.pdf` — Khái niệm chung: mạng máy tính, kiến trúc mạng, phân tầng, lập trình mạng
- `Bài 3.pdf` — Các mô hình ứng dụng mạng (Client/Server)
- `Bài 4.pdf` — Lập trình với giao thức TCP
- `Bài 5.pdf` — **Lập trình với giao thức UDP** (chứa đề bài này, slide 16–17)
- `2013-Advanced Network Programming - Principles and Techniques.pdf`

### RFC liên quan
| RFC | Nội dung |
|---|---|
| RFC 768 | UDP — User Datagram Protocol |
| RFC 793 | TCP — Transmission Control Protocol |
| RFC 5321 | SMTP — Simple Mail Transfer Protocol (làm mới RFC 821) |
| RFC 5322 | Internet Message Format (làm mới RFC 822) |
| RFC 1939 | POP3 — Post Office Protocol v3 |
| RFC 3501 | IMAP — Internet Message Access Protocol |
| RFC 2045–2049 | MIME — Multipurpose Internet Mail Extensions |
| RFC 1939 §, RFC 1939 | POP3 |
| RFC 6376 | DKIM |
| RFC 7208 | SPF |
| RFC 7489 | DMARC |
| RFC 5321 §4.5 | Dấu chấm kết thúc DATA |
| CVE-2021-34527 | Path traversal qua header (bài học bảo mật) |

### Tài liệu Java
- `java.net.DatagramSocket` — https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/DatagramSocket.html
- `java.net.DatagramPacket` — https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/DatagramPacket.html
- `java.nio.charset.StandardCharsets` — https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/StandardCharsets.html

---

*Biên soạn cho Lab 5 – Bài 2: Mail Server (UDP Socket) — Môn Lập trình Mạng, 24IT062*
