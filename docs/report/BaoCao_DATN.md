TRƯỜNG ĐẠI HỌC NGUYỄN TRÃI

KHOA CÔNG NGHỆ THÔNG TIN & CHUYỂN ĐỔI SỐ

ĐỒ ÁN

TỐT NGHIỆP ĐẠI HỌC

ĐỀ TÀI

Xây dựng sàn thương mại điện tử đặc sản vùng miền tích hợp Chatbot tư vấn bán hàng

Ngành:  Công nghệ thông tin

Giảng viên hướng dẫn: ThS. Nguyễn Thị Duyên

Chữ ký GVHD

Hà Nội, 12/2026

LỜI CẢM ƠN

Để hoàn thành đồ án tốt nghiệp này, em đã nhận được sự giúp đỡ của nhiều người.

Trước hết, em xin gửi lời cảm ơn đến ThS. Nguyễn Thị Duyên, giảng viên hướng dẫn, người đã góp ý và định hướng cho em trong suốt quá trình thực hiện đề tài. Em cũng cảm ơn quý thầy cô Khoa Công nghệ thông tin & Chuyển đổi số, Trường Đại học Nguyễn Trãi, đã truyền đạt những kiến thức nền tảng để em có thể thực hiện đồ án.

Em xin cảm ơn gia đình đã luôn ủng hộ và tạo điều kiện để em tập trung học tập. Em cảm ơn bạn bè đã chia sẻ kinh nghiệm, góp ý và hỗ trợ em khi gặp khó khăn trong quá trình làm đồ án.

Cuối cùng, em cũng ghi nhận sự nỗ lực của chính mình. Trong thời gian vừa qua, em đã chủ động tìm hiểu công nghệ mới, kiên trì sửa lỗi và hoàn thiện sản phẩm từng bước cho đến khi hoàn thành.

Do hiểu biết và thời gian có hạn, đồ án khó tránh khỏi thiếu sót. Em rất mong nhận được ý kiến đóng góp của quý thầy cô để đề tài được hoàn thiện hơn.

Em xin chân thành cảm ơn.

Hà Nội, ngày … tháng … năm 2026.

Sinh viên

Nguyễn Hữu Tuyên


# TÓM TẮT NỘI DUNG ĐỒ ÁN


Đặc sản vùng miền là nhóm sản phẩm có giá trị văn hóa và kinh tế lớn, nhưng người sản xuất nhỏ lẻ thường gặp khó khăn trong việc tiếp cận người mua trên phạm vi cả nước, còn người mua khó tìm được sản phẩm đúng nguồn gốc và có thông tin rõ ràng. Hiện nay, việc kinh doanh đặc sản chủ yếu diễn ra qua ba hướng: bán trên các sàn thương mại điện tử đại trà, bán qua mạng xã hội và ứng dụng nhắn tin, hoặc xây dựng website riêng cho từng cơ sở. Các sàn đại trà có lượng khách lớn nhưng đặc sản bị lẫn với nhiều nhóm hàng khác, khó lọc theo vùng miền. Bán qua mạng xã hội thiếu quy trình đặt hàng, thanh toán và theo dõi đơn thống nhất. Website riêng đòi hỏi chi phí và kỹ năng mà các cơ sở nhỏ khó đáp ứng. Từ đó, đề tài lựa chọn hướng xây dựng một sàn thương mại điện tử chuyên biệt cho đặc sản vùng miền, cho phép nhiều người bán cùng hoạt động và tích hợp chatbot AI để tư vấn cho người mua. Hướng này được chọn vì vừa tập trung đúng nhóm sản phẩm, vừa giảm chi phí cho người bán nhỏ và hỗ trợ người mua trong lúc chọn sản phẩm. Hệ thống VietSpecialty được xây dựng trên nền tảng Java Spring Boot và cơ sở dữ liệu MySQL, giao diện sử dụng Tailwind CSS, gồm các chức năng quản lý gian hàng, sản phẩm theo vùng miền, giỏ hàng, đặt hàng, thanh toán, vận chuyển, đánh giá, khuyến mãi và thống kê. Chatbot AI tư vấn dựa trên dữ liệu sản phẩm thực tế của sàn. Đóng góp chính của đồ án là một sản phẩm hoàn chỉnh cho người mua, người bán và quản trị viên, kèm tài liệu phân tích, thiết kế

MỤC LỤC


# DANH MỤC CÁC TỪ VIẾT TẮT



# DANH MỤC BẢNG BIỂU


Bảng  2.1 Một số chức năng chính	5

Bảng  2.2 Yêu cầu phi chức năng	5

Bảng  2.3. Một số chức năng chính	6

Bảng  2.4. Bảng đặc tả use case …..	7

Bảng  2.5. Bảng đặc tả use case …..	8

Bảng  3.1. Lớp ….	15

Bảng  3.2. Lớp …..	15

Bảng  3.3. …..	15

Bảng  3.4. Bảng…..	16

Bảng  3.5. Bảng…..	16

Bảng  3.6. Bảng…..	16

Bảng  3.7. Bảng…..	17

Bảng  4.1. Kiểm thử chức năng …………	20

Bảng  4.2. Kiểm thử chức năng ………….	20

Bảng  4.3. Kiểm thử chức năng ………….	20


# DANH MỤC HÌNH ẢNH


Hình 1.1. Doanh thu thị trường quần áo tại Việt Nam (2017-2021)	3

Hình 2.1. Sơ đồ Use Case tổng quát	7

Hình 2.2. Usecase Quản lý tài khoản….	7

Hình 2.3. Usecase Tìm kiếm sản phẩm  ….	8

Hình 2.4. Sơ đồ Đặt hàng	9

Hình 2.5. Sơ đồ phân rã Quản lý sản phẩm	10

Hình 2.6. Use case phân rã Xử lý đơn hàng ……	11

Hình 2.7. Use case phân rã Chatbot ……	12

Hình 3.1. Thiết kế tổng quát gói……….	13

Hình 3.2. Thiết kế chi tiết gói………..	14

Hình 3.3. Thiết kế giao diện trang chủ	14

Hình 3.4. Thiết kế giao diện Mobile……..	15

Hình 3.5. Sơ đồ lớp……	16

Hình 3.6. E-R diagram ……..	18

Hình 4.1. Giao diện trang chủ	19

Hình 4.2. Giao diện………..	19


# MỞ ĐẦU



## Đặt vấn đề


Thương mại điện tử đã trở thành kênh mua sắm quen thuộc của người tiêu dùng Việt Nam, kể cả với những mặt hàng trước đây chủ yếu được mua trực tiếp. Đặc sản vùng miền là một ví dụ. Mỗi địa phương có những sản phẩm riêng như nước mắm, chè, cà phê, mật ong, bánh, mứt, nem, chả. Người mua thường chọn đặc sản làm quà biếu, quà Tết hoặc mua để dùng khi ở xa quê. Chương trình Mỗi xã một sản phẩm (OCOP) cũng giúp nhiều sản phẩm được chuẩn hóa về chất lượng và bao bì.

Tuy vậy, phần lớn người làm đặc sản là hộ gia đình, hợp tác xã và cơ sở nhỏ, thiếu vốn, kỹ năng số và khả năng quảng bá. Sản phẩm chủ yếu được bán tại địa phương, qua thương lái hoặc qua tin nhắn cho người quen. Vì vậy thị trường bị bó hẹp, giá tăng qua nhiều tầng trung gian, còn việc ghi đơn và theo dõi doanh thu đều làm thủ công. Về phía người mua, việc tìm đặc sản đúng vùng miền, có thông tin rõ ràng về xuất xứ, hạn sử dụng và người bán đáng tin cậy vẫn còn khó. Trên các sàn đại trà, đặc sản bị lẫn với rất nhiều mặt hàng khác, và người mua thường không biết nên chọn sản phẩm nào cho nhu cầu của mình.

Giải quyết khoảng cách giữa nguồn cung đặc sản ở địa phương và nhu cầu của người mua trên cả nước mang lại lợi ích cho nhiều bên. Người sản xuất nhỏ có thêm thị trường. Người mua tiếp cận đặc sản dễ dàng và minh bạch hơn. Địa phương có thêm kênh quảng bá sản phẩm. Bài toán này cũng có thể áp dụng cho các nhóm hàng tương tự như nông sản, sản phẩm OCOP và hàng thủ công mỹ nghệ.


## Mục tiêu đề tài


Mục tiêu tổng quát của đề tài là xây dựng VietSpecialty, một sàn thương mại điện tử chuyên về đặc sản vùng miền. Trên sàn, nhiều người bán cùng mở gian hàng, người mua tìm và đặt mua đặc sản thuận tiện, và chatbot AI hỗ trợ tư vấn sản phẩm.

Để đạt mục tiêu này, đề tài đặt ra các mục tiêu cụ thể. Thứ nhất, khảo sát và phân tích yêu cầu, xây dựng mô hình nghiệp vụ cho người mua, người bán và quản trị viên. Thứ hai, thiết kế kiến trúc hệ thống, cơ sở dữ liệu và giao diện. Thứ ba, xây dựng các chức năng quản lý tài khoản, gian hàng, sản phẩm theo vùng miền, giỏ hàng, đặt hàng, thanh toán, theo dõi đơn hàng, đánh giá, mã giảm giá và thống kê. Thứ tư, tích hợp chatbot AI để gợi ý đặc sản theo vùng miền và nhu cầu, giải đáp thắc mắc và tra cứu đơn hàng. Cuối cùng, kiểm thử các chức năng, đánh giá chất lượng phản hồi của chatbot và hoàn thiện tài liệu.

Kết quả dự kiến là một website hoạt động được trên máy tính và điện thoại, kèm báo cáo phân tích, thiết kế và kiểm thử hệ thống.


## Phạm vi đề tài


. Đối tượng nghiên cứu của đề tài là mô hình sàn thương mại điện tử nhiều người bán trong lĩnh vực đặc sản vùng miền và việc ứng dụng chatbot AI vào tư vấn bán hàng. Đối tượng sử dụng hệ thống gồm khách vãng lai, người mua, người bán và quản trị viên. Hệ thống được triển khai dưới dạng ứng dụng web, chạy trên các trình duyệt phổ biến của máy tính và thiết bị di động.

Các chức năng được thực hiện gồm: đăng ký, đăng nhập và phân quyền; quản lý gian hàng và sản phẩm theo vùng miền; tìm kiếm, lọc sản phẩm; giỏ hàng; đặt hàng tách theo gian hàng; thanh toán khi nhận hàng và thanh toán trực tuyến; xử lý và theo dõi đơn hàng; đánh giá, bình luận; mã giảm giá; thống kê cho người bán và quản trị viên; chatbot tư vấn và tra cứu đơn hàng.

Đề tài không phát triển ứng dụng di động riêng và không tự huấn luyện mô hình AI mà sử dụng mô hình ngôn ngữ có sẵn qua API. Đề tài cũng không xử lý các vấn đề pháp lý như chứng nhận an toàn thực phẩm hay xuất khẩu. Phạm vi được giới hạn như trên để phù hợp với thời gian và nguồn lực của một đồ án tốt nghiệp.


## Giải pháp công nghệ


. Đề tài chọn hướng xây dựng một sàn thương mại điện tử chuyên biệt cho đặc sản, cho phép nhiều người bán cùng hoạt động, thay vì bán trên sàn đại trà hay làm website riêng cho từng cơ sở. Hướng này tập trung đúng nhóm sản phẩm, hỗ trợ tìm kiếm theo vùng miền và giảm chi phí cho người bán nhỏ.

Hệ thống được xây dựng bằng Java Spring Boot theo kiến trúc phân tầng, cung cấp REST API, và lưu dữ liệu trong MySQL. Giao diện dùng Tailwind CSS để hiển thị tốt trên nhiều kích thước màn hình. Đây là các công nghệ phổ biến, tài liệu đầy đủ và phù hợp với năng lực thực hiện. Chatbot dùng mô hình ngôn ngữ lớn qua API, kết hợp với dữ liệu sản phẩm lấy từ cơ sở dữ liệu của sàn, để chỉ tư vấn những sản phẩm có thật.

Đóng góp chính của đồ án là một sàn thương mại điện tử hoàn chỉnh phục vụ người mua, người bán và quản trị viên, có tích hợp chatbot tư vấn đặc sản theo vùng miền, cùng bộ tài liệu phân tích, thiết kế và kiểm thử


## Bố cục của đề tài


Ngoài phần Mở đầu, Kết luận và Danh mục tài liệu tham khảo, nội dung đồ án được chia thành các chương chính như sau:

Chương 1: Tổng quan về đề tài và nền tảng công nghệ

Sinh viên cần viết mô tả thành đoạn văn đầy đủ về nội dung chương. Tuyệt đối không viết ý hay gạch đầu dòng

Chương 2. Khảo sát và phân tích hệ thống

Sinh viên cần viết mô tả thành đoạn văn đầy đủ về nội dung chương. Tuyệt đối không viết ý hay gạch đầu dòng

Chương 3. Thiết kế hệ thống

Sinh viên cần viết mô tả thành đoạn văn đầy đủ về nội dung chương. Tuyệt đối không viết ý hay gạch đầu dòng

Chương 4. xây dựng ứng dụng

Sinh viên cần viết mô tả thành đoạn văn đầy đủ về nội dung chương. Tuyệt đối không viết ý hay gạch đầu dòng


# TỔNG QUAN ĐỀ TÀI VÀ NỀN TẢNG CÔNG NGHỆ


Chương này trình bày bối cảnh và cơ sở công nghệ của đề tài. Phần đầu khái quát lĩnh vực kinh doanh đặc sản vùng miền và thị trường thương mại điện tử tại Việt Nam. Tiếp theo, chương mô tả mô hình mua bán đặc sản truyền thống, phân tích ưu điểm, hạn chế của mô hình này và xu hướng ứng dụng công nghệ trong lĩnh vực. Phần cuối giới thiệu các công nghệ được sử dụng để xây dựng sàn VietSpecialty, gồm Spring Boot, REST API, MySQL, Tailwind CSS và API mô hình ngôn ngữ lớn dùng cho chatbot.


## Tổng quan về đề tài sàn thương mại điện tử đặc sản vùng miền



### Khái quát về lĩnh vực kinh doanh đặc sản vùng miền


Đặc sản vùng miền là những sản phẩm gắn với một địa phương cụ thể, được làm ra từ nguyên liệu, khí hậu, thổ nhưỡng và kinh nghiệm sản xuất riêng của nơi đó. Ở Việt Nam, nhóm sản phẩm này rất đa dạng. Có thể kể đến nước mắm, mắm, các loại khô ở vùng ven biển; chè, cà phê, mật ong ở vùng trung du và Tây Nguyên; các loại bánh, mứt, nem, chả, giò ở nhiều tỉnh đồng bằng; trái cây sấy, hạt điều, kẹo dừa ở miền Nam. Nhiều sản phẩm đã trở thành dấu hiệu nhận biết của địa phương và thường được người mua chọn làm quà biếu, quà Tết hoặc mua về dùng khi ở xa quê.

Nhà nước đã có chương trình riêng để phát triển nhóm sản phẩm này. Chương trình Mỗi xã một sản phẩm (OCOP) đánh giá, phân hạng sản phẩm địa phương theo thang từ 1 đến 5 sao. Đến tháng 9/2025, cả nước có gần 17.400 sản phẩm OCOP đạt từ 3 sao trở lên, do hơn 9.300 chủ thể sản xuất, tăng hơn 8.400 sản phẩm so với năm 2022 [7]. Hơn 60,7% chủ thể OCOP có doanh thu tăng, với mức tăng bình quân 17,6% mỗi năm [7]. Một trong những định hướng tiếp theo của chương trình là ứng dụng công nghệ số, truy xuất nguồn gốc và đưa sản phẩm lên các sàn thương mại điện tử [7].

Song song với đó, thương mại điện tử bán lẻ tại Việt Nam tăng trưởng liên tục. Theo số liệu được Báo Công Thương tổng hợp, quy mô thị trường đạt khoảng 8 tỷ USD năm 2018, 11,8 tỷ USD năm 2020, 16,4 tỷ USD năm 2022 và 20,5 tỷ USD năm 2023. Năm 2024 quy mô thị trường vượt 25 tỷ USD, tăng khoảng 20% và chiếm khoảng 9% tổng mức bán lẻ hàng hóa, dịch vụ tiêu dùng [6]. Hình 1.1 thể hiện sự tăng trưởng này.

Hình 1.1. Doanh thu thị trường quần áo tại Việt Nam (2017-2021)


### Mô hình hoạt động truyền thống trong lĩnh vực kinh doanh đặc sản


. Quy trình hoạt động. Trong mô hình truyền thống, đặc sản đi từ nơi sản xuất tới người mua qua ba con đường chính. Con đường thứ nhất là bán trực tiếp: cơ sở sản xuất bán tại nhà, tại chợ địa phương, tại điểm du lịch hoặc các hội chợ, phiên chợ OCOP. Con đường thứ hai là qua trung gian: thương lái, đại lý hoặc cửa hàng đặc sản mua sỉ từ cơ sở sản xuất rồi bán lẻ cho người mua. Con đường thứ ba là bán cho khách ở xa theo cách thủ công. Người mua là người quen hoặc khách cũ, liên hệ qua điện thoại, Zalo hoặc Facebook. Người bán ghi đơn bằng tay, đóng gói và gửi qua nhà xe, bưu điện hoặc đơn vị giao hàng. Tiền được thu khi giao hàng hoặc chuyển khoản trước.

Thành phần tham gia. Mô hình gồm cơ sở sản xuất (hộ gia đình, hợp tác xã, doanh nghiệp nhỏ), các trung gian phân phối (thương lái, đại lý, cửa hàng đặc sản), đơn vị vận chuyển (nhà xe, bưu điện, công ty giao hàng) và người mua. Công cụ được sử dụng chủ yếu là sổ ghi chép, điện thoại, ứng dụng nhắn tin, trang mạng xã hội cá nhân và tài khoản ngân hàng để nhận tiền.

Ưu điểm và hạn chế. Mô hình truyền thống có chi phí ban đầu thấp và dễ thực hiện, phù hợp với quy mô nhỏ. Người bán và người mua quen biết nhau nên dễ tạo được sự tin tưởng. Người mua tại chỗ có thể xem, nếm thử sản phẩm trước khi mua.

Mô hình này cũng có nhiều hạn chế. Về thị trường, người bán chỉ tiếp cận được khách ở địa phương, khách du lịch hoặc người quen; khách ở tỉnh khác khó biết tới sản phẩm. Về chi phí, khi bán qua nhiều tầng trung gian, giá tới tay người mua tăng lên trong khi phần người sản xuất nhận được không tăng tương ứng. Về thời gian và hiệu quả, việc nhận đơn, trả lời câu hỏi, báo giá, xác nhận chuyển khoản đều làm thủ công, mất nhiều thời gian và dễ nhầm lẫn khi số đơn tăng. Về quản lý, người bán khó theo dõi tồn kho, doanh thu, đơn hàng đang giao và sản phẩm bán chạy. Về phía người mua, thông tin xuất xứ, hạn sử dụng, cách bảo quản thường không đầy đủ. Người mua không có kênh so sánh nhiều người bán và không có đánh giá của người mua trước để tham khảo.

Minh họa thực tế. Hình 1.2 mô tả các con đường đưa đặc sản tới người mua trong mô hình truyền thống. Ví dụ, một hộ làm mứt gừng ở địa phương thường bán sỉ cho các cửa hàng đặc sản trong tỉnh, bán lẻ tại nhà vào dịp Tết, và nhận thêm đơn của người quen ở Hà Nội hoặc TP. Hồ Chí Minh qua Zalo. Với đơn ở xa, hộ phải tự ghi lại địa chỉ, tự đóng gói, gửi nhà xe và theo dõi việc thu tiền.

Hình 1.2. Mô hình phân phối đặc sản truyền thống


### Xu hướng ứng dụng công nghệ trong lĩnh vực  kinh doanh đặc sản


Tổng quan xu hướng công nghệ: Phân tích các xu hướng công nghệ nổi bật đang được ứng dụng trong lĩnh vực nghiên cứu của đề tài.

Các công nghệ tiêu biểu: Giới thiệu những công nghệ, phương pháp, nền tảng hoặc công cụ đang được sử dụng; làm rõ vai trò và khả năng ứng dụng của từng công nghệ.

Tình hình ứng dụng thực tiễn: Phân tích cách các công nghệ được áp dụng trong thực tế, tại Việt Nam và trên thế giới; đưa ra ví dụ hoặc sản phẩm, hệ thống tiêu biểu.

Lợi ích và hạn chế: Làm rõ những lợi ích, cơ hội, thách thức và hạn chế khi ứng dụng các công nghệ này.

Xu hướng phát triển: Nhận định chiều hướng phát triển của công nghệ trong thời gian tới và khả năng áp dụng vào bài toán nghiên


## Nền tảng công nghệ



### Tìm hiểu về SpringBoot


Spring Boot là framework Java mã nguồn mở. Framework này tự động cấu hình các thành phần cần thiết và có sẵn máy chủ web nhúng, nên ứng dụng chạy được ngay mà không cần triển khai lên máy chủ riêng. Ứng dụng được chia thành ba tầng: Controller, Service và Repository. Đề tài chọn Spring Boot vì framework này phổ biến, có tài liệu đầy đủ và hỗ trợ tốt việc phân quyền, xử lý nghiệp vụ.


### Tìm hiểu về REST API


REST API là cách thiết kế giao tiếp giữa máy khách và máy chủ qua HTTP. Mỗi dữ liệu (sản phẩm, đơn hàng) là một tài nguyên có URL riêng và được thao tác bằng các phương thức GET, POST, PUT, DELETE. Dữ liệu trao đổi ở dạng JSON. Trong đề tài, REST API kết nối giao diện với máy chủ, đồng thời kết nối hệ thống với cổng thanh toán và dịch vụ AI.


### Tìm hiểu về MySQL


MySQL là hệ quản trị cơ sở dữ liệu quan hệ mã nguồn mở, dùng ngôn ngữ SQL. MySQL hỗ trợ khóa ngoại, giao dịch (transaction) và chỉ mục, giúp dữ liệu chính xác và truy vấn nhanh. Đề tài chọn MySQL vì nó miễn phí, phổ biến và tích hợp dễ với Spring Boot.


### Tìm hiểu về Tailwind CSS


Tailwind CSS là framework CSS cung cấp các lớp tiện ích nhỏ để ghép thành giao diện ngay trong HTML. Tailwind hỗ trợ sẵn thiết kế responsive cho điện thoại và máy tính. Đề tài dùng Tailwind CSS để làm giao diện thống nhất cho người mua, người bán và quản trị viên.


### Tìm hiểu về API mô hình ngôn ngữ lớn cho chatbot


Mô hình ngôn ngữ lớn (LLM) có thể hiểu và trả lời câu hỏi bằng tiếng Việt, và được sử dụng qua API. Mô hình không biết dữ liệu riêng của sàn. Vì vậy, khi người dùng hỏi, hệ thống lấy các sản phẩm liên quan trong cơ sở dữ liệu rồi gửi kèm câu hỏi tới mô hình. Nhờ đó, chatbot chỉ gợi ý những sản phẩm có thật trên sàn.


# KHẢO SÁT VÀ PHÂN TÍCH HỆ THỐNG


Chương này trình bày kết quả khảo sát và phân tích yêu cầu của sàn thương mại điện tử VietSpecialty. Nội dung gồm các chức năng chính, yêu cầu phi chức năng, các tác nhân tham gia, quy trình nghiệp vụ và biểu đồ use case tổng quát của hệ thống. Tiếp theo, chương đặc tả chi tiết các use case quan trọng, sau đó trình bày biểu đồ tuần tự và biểu đồ hoạt động cho các chức năng đặt hàng, tư vấn qua chatbot và xử lý đơn hàng. Kết quả phân tích là cơ sở để thiết kế hệ thống ở Chương 3.


## Chức năng của hệ thống


Phần này tóm tắt ở mức tổng quan các chức năng mà VietSpecialty cung cấp cho từng nhóm người dùng. Đặc tả chi tiết các chức năng quan trọng được trình bày ở mục 2.2


### Một số chức năng chính


Hệ thống website quản lý bán thuốc được xây dựng nhằm đáp ứng nhu cầu mua bán thuốc trực tuyến của khách hàng và hỗ trợ hiệu quả các nghiệp vụ quản lý cho người quản trị hệ thống.

Bảng  2.1 Một số chức năng chính


### Yêu cầu phi chức năng


Hệ thống cần đảm bảo tính ổn định, bảo mật và thân thiện với người dùng.

Bảng  2.2 Yêu cầu phi chức năng


### Các tác nhân tham gia hệ thống


Từ hoạt động mua bán đặc sản, các đối tượng tương tác với VietSpecialty được chia thành hai nhóm. Nhóm thứ nhất là con người, gồm khách vãng lai, người mua, người bán và quản trị viên. Nhóm thứ hai là các hệ thống bên ngoài mà sàn cần kết nối, gồm cổng thanh toán và dịch vụ AI dùng cho chatbot. Giữa các tác nhân con người có quan hệ kế thừa. Người mua có mọi quyền của khách vãng lai, cộng thêm quyền giao dịch. Người bán là người mua đã được duyệt mở gian hàng, nên vẫn dùng được các chức năng mua hàng.


### Quy trình nghiệp vụ trong hệ thống


Hoạt động nghiệp vụ của VietSpecialty gồm bán hàng (người bán đăng sản phẩm, xử lý đơn), mua hàng (người mua tìm kiếm, đặt hàng, thanh toán, đánh giá), hỗ trợ khách hàng (chatbot tư vấn và tra cứu đơn) và quản trị (quản trị viên duyệt gian hàng, quản lý danh mục, khuyến mãi, thống kê). Hai quy trình cốt lõi được mô tả dưới đây.

Quy trình mua hàng và xử lý đơn hàng. Đối tượng tham gia gồm người mua, người bán, hệ thống và cổng thanh toán. Mục đích của quy trình là đưa sản phẩm tới người mua, đồng thời giữ đúng tồn kho và trạng thái đơn hàng.

Bước 1: Người mua tìm kiếm, lọc sản phẩm theo vùng miền và thêm sản phẩm vào giỏ hàng.

Bước 2: Người mua nhấn “Mua hàng”, chọn địa chỉ, phương thức vận chuyển và nhập mã giảm giá (nếu có).

Bước 3: Người mua chọn thanh toán khi nhận hàng hoặc thanh toán trực tuyến, rồi xác nhận đặt hàng.

Bước 4: Hệ thống kiểm tra tồn kho, tách đơn theo từng gian hàng và trừ tồn kho. Đơn thanh toán khi nhận hàng chuyển sang “Chờ xác nhận”. Đơn thanh toán trực tuyến được chuyển sang cổng thanh toán và chỉ chuyển sang “Chờ xác nhận” khi thanh toán thành công.

Bước 5: Người bán nhận đơn, rồi xác nhận (đơn chuyển sang “Đang chuẩn bị”) hoặc từ chối (đơn chuyển sang “Đã hủy” và tồn kho được hoàn lại).

Bước 6: Người bán giao hàng cho đơn vị vận chuyển và cập nhật đơn sang “Đang giao”.

Bước 7: Khi người mua nhận hàng, đơn chuyển sang “Đã giao”. Người mua có thể đánh giá sản phẩm, và doanh thu được đưa vào thống kê.

Quy trình tư vấn qua chatbot. Đối tượng tham gia gồm người dùng, hệ thống và dịch vụ AI. Mục đích là giúp người dùng chọn được đặc sản phù hợp và tự tra cứu đơn hàng.

Bước 1: Người dùng mở khung chat và nhập câu hỏi bằng tiếng Việt.

Bước 2: Hệ thống xác định loại yêu cầu: tư vấn sản phẩm, hỏi chính sách hay tra cứu đơn hàng.

Bước 3: Với yêu cầu tư vấn, hệ thống truy vấn các sản phẩm đang bán, phù hợp vùng miền, loại sản phẩm, mức giá. Với yêu cầu tra cứu, hệ thống chỉ lấy đơn hàng của chính người dùng đã đăng nhập.

Bước 4: Hệ thống gửi câu hỏi kèm dữ liệu vừa lấy được tới dịch vụ AI.

Bước 5: Dịch vụ AI trả lời. Hệ thống hiển thị câu trả lời kèm các sản phẩm gợi ý hoặc thông tin đơn hàng

.


### Các tác nhân tham gia hệ thống


Khách vãng lai là người chưa đăng nhập, có mục đích tìm hiểu sản phẩm trước khi mua. Họ được xem, tìm kiếm sản phẩm, đọc đánh giá và hỏi chatbot.

Người mua là người đã có tài khoản và đăng nhập. Người mua thực hiện mua hàng, thanh toán, theo dõi đơn, đánh giá sản phẩm và có trách nhiệm cung cấp đúng thông tin nhận hàng.

Người bán là cơ sở sản xuất hoặc hộ kinh doanh đặc sản có gian hàng đã được duyệt. Người bán quản lý sản phẩm, xử lý đơn hàng và chịu trách nhiệm về thông tin sản phẩm cũng như việc giao hàng.

Quản trị viên là người vận hành sàn. Quản trị viên duyệt gian hàng, quản lý người dùng, danh mục, khuyến mãi, kiểm duyệt nội dung và theo dõi thống kê.

Cổng thanh toán là hệ thống bên ngoài xử lý giao dịch thanh toán trực tuyến và trả kết quả về hệ thống.

Dịch vụ AI là mô hình ngôn ngữ cung cấp qua API. Dịch vụ này sinh câu trả lời cho chatbot dựa trên dữ liệu hệ thống gửi sang

..

Bảng  2.3. Một số chức năng chính


### Biểu đồ use case tổng quát


Biểu đồ use case tổng quát gồm sáu tác nhân. Người mua kế thừa Khách vãng lai, và Người bán kế thừa Người mua.

Khách vãng lai sử dụng các use case: Đăng ký tài khoản, Tìm kiếm sản phẩm, Xem chi tiết sản phẩm, Tư vấn qua chatbot.

Người mua sử dụng thêm: Đăng nhập, Quản lý tài khoản, Quản lý giỏ hàng, Đặt hàng, Theo dõi đơn hàng, Đánh giá sản phẩm, Tra cứu đơn hàng qua chatbot, Đăng ký mở gian hàng.

Người bán sử dụng: Quản lý gian hàng, Quản lý sản phẩm, Xử lý đơn hàng, Quản lý mã giảm giá, Xem thống kê gian hàng.

Quản trị viên sử dụng: Đăng nhập, Duyệt gian hàng, Quản lý người dùng, Quản lý danh mục và vùng miền, Quản lý khuyến mãi, Kiểm duyệt nội dung, Xem thống kê toàn sàn.

Use case Áp dụng mã giảm giá và Thanh toán trực tuyến mở rộng (extend) use case Đặt hàng. Cổng thanh toán nối với Thanh toán trực tuyến. Dịch vụ AI nối với Tư vấn qua chatbot và Tra cứu đơn hàng qua chatbot

.

Hình 2.1. Sơ đồ Use Case tổng quát


## Biểu đồ use case phân rã chức năng


Mục này đặc tả sáu use case quan trọng nhất của hệ thống.


### Đặc tả use case Đăng ký tài khoản


Hình 2.2. Usecase Đăng ký tài khoản

Bảng  2.4. Bảng đặc tả use case …..


### Đặc tả use case Đăng Nhập


Hình 2.3. Usecase Đăng Nhập

Bảng  2.5. Bảng đặc tả use case Tìm kiếm sản phẩm


### Đặc tả use case Quản Lý Giỏ Hàng


Hình 2.4. Usecase Quản Lý Giỏ Hàng

Bảng 2.6. Bảng đặc tả use case Quản Lý Giỏ Hàng


### Đặc tả use case Đặt hàng & Thanh toán VNPay


Hình 2.5. Usecase Đặt hàng & Thanh toán VNPay

Bảng 2.7. Bảng đặc tả use case Đặt hàng & Thanh toán VNPay


### Đặc tả use case Đăng ký mở gian hàng


Hình 2.6. Use case Đăng ký mở gian hàng

Bảng 2.8. Bảng đặc tả use case Đăng ký mở gian hàng


### Đặc tả use case Quản Lý Sản Phẩm


Hình 2.7. Use case Quản Lý Sản Phẩm

Bảng 2.9. Bảng đặc tả use case Quản Lý Sản Phẩm


### Đặc Tả Use case Đánh Giá  Sản Phẩm


Hình 2.8. Use case Đánh Giá  Sản Phẩm

Bảng 2.10. Bảng đặc tả use case Đánh giá sản phẩm


### Đặc Tả Use case Xử Lý Đơn Hàng


Hình 2.9. Xử Lý Đơn Hàng

Bảng 2.11. Bảng đặc tả use case: Đánh giá sản phẩm


### Đặc tả Use case Tư vấn qua Chatbot AI


Hình 2.10. Use case Tư vấn qua Chatbot AI

Bảng 2.12. Bảng đặc tả use case Tư vấn qua Chatbot AI


### Đặc tả use case Theo dõi đơn hàng


Hình 2.11. Use case Theo dõi đơn hàng

Bảng 2.13. Bảng đặc tả use case Theo dõi đơn hàng


## Sequence diagram



### Biểu đồ tuần tự đặt hàng


Biểu đồ gồm các đối tượng: Người mua, Giao diện đặt hàng, OrderController, OrderService, Cơ sở dữ liệu và Cổng thanh toán (VNPay). Trình tự diễn ra như sau:

Người mua nhấn nút “Đặt hàng” trên giao diện đặt hàng.

Giao diện gửi yêu cầu đặt hàng tới OrderController kèm thông tin giỏ hàng, địa chỉ giao, mã giảm giá và phương thức thanh toán.

OrderController gọi OrderService để xử lý nghiệp vụ tạo đơn hàng.

OrderService truy vấn thông tin sản phẩm và tồn kho từ Cơ sở dữ liệu.

OrderService thực hiện kiểm tra: tồn kho có đủ số lượng không, mã giảm giá có hợp lệ không, sau đó tính tổng tiền cần thanh toán.

OrderService lưu đơn hàng theo từng gian hàng vào Cơ sở dữ liệu và đồng thời trừ tồn kho tương ứng.

Nếu người mua chọn thanh toán trực tuyến qua VNPay, OrderService gửi yêu cầu tạo giao dịch tới Cổng thanh toán và nhận về đường dẫn thanh toán. Nếu chọn thanh toán khi nhận hàng (COD), bước này được bỏ qua.

OrderController trả kết quả về giao diện. Giao diện thông báo đặt hàng thành công hoặc chuyển hướng người mua sang trang VNPay để hoàn tất thanh toán.

Hình 2.14. Biểu đồ tuần tự đặt hàng


### Biểu đồ tuần tự tư vấn qua chatbot


Biểu đồ gồm các đối tượng: Người dùng, Giao diện Web, ChatController, ChatService, Cơ sở dữ liệu và Gemini API. Trình tự diễn ra như sau:

Người dùng nhập câu hỏi tư vấn vào giao diện chatbot và nhấn gửi.

Giao diện gửi tin nhắn tới ChatController kèm nội dung câu hỏi và mã phiên làm việc (sessionId).

ChatController chuyển yêu cầu sang ChatService để xử lý.

ChatService truy vấn Cơ sở dữ liệu để lấy lịch sử hội thoại gần nhất của phiên làm việc, giúp chatbot hiểu được ngữ cảnh cuộc trò chuyện.

ChatService tiếp tục truy vấn danh sách sản phẩm liên quan từ bảng products dựa trên từ khóa trong câu hỏi.

ChatService tổng hợp và tạo prompt hoàn chỉnh gồm: hướng dẫn hệ thống, danh sách sản phẩm, lịch sử hội thoại và câu hỏi của người dùng.

ChatService gửi prompt lên Gemini API để sinh câu trả lời.

Gemini API phân tích và trả về nội dung phản hồi kèm danh sách sản phẩm gợi ý phù hợp.

ChatService lưu tin nhắn của người dùng và phản hồi của bot vào bảng chat_messages trong Cơ sở dữ liệu.

Kết quả được trả về giao diện, hiển thị câu trả lời và các thẻ sản phẩm gợi ý cho người dùng.

Hình 2.15. Biểu đồ tuần tự tư vấn qua chatbot


## Activy diagram



### Biểu đồ hành động đặt hàng


Biểu đồ chia ba làn: Người mua, Hệ thống, Cổng thanh toán. Luồng hoạt động:

Người mua chọn sản phẩm trong giỏ, nhấn “Mua hàng”.

Người mua chọn địa chỉ, phương thức vận chuyển, nhập mã giảm giá (nếu có).

Hệ thống kiểm tra mã. Mã không hợp lệ thì thông báo; mã hợp lệ thì tính lại tiền.

Người mua chọn phương thức thanh toán, nhấn “Đặt hàng”.

Hệ thống kiểm tra tồn kho. Không đủ hàng thì thông báo và kết thúc.

Nếu thanh toán khi nhận hàng: hệ thống tạo đơn, thông báo thành công và kết thúc.

Nếu thanh toán trực tuyến: Cổng thanh toán xử lý giao dịch. Thành công thì hệ thống tạo đơn; thất bại thì thông báo cho người mua

Hình 2.10. Biểu đồ hành động đặt hàng


### Biểu đồ hành động xử lý đơn hàng


Biểu đồ chia hai làn: Người bán, Hệ thống. Luồng hoạt động:

Hệ thống thông báo có đơn mới cho người bán.

Người bán xem chi tiết đơn.

Người bán quyết định: từ chối thì hệ thống hủy đơn, hoàn tồn kho và kết thúc; xác nhận thì hệ thống chuyển đơn sang “Đang chuẩn bị”.

Người bán đóng gói và giao cho đơn vị vận chuyển. Hệ thống chuyển đơn sang “Đang giao”.

Khi giao thành công, người bán cập nhật. Hệ thống chuyển đơn sang “Đã giao” và kết thúc.

Hình 2.11. Biểu đồ hành động xử lý đơn hàng


# THIẾT KẾ HỆ THỐNG VÀ CƠ SỞ DỮ LIỆU


Mục này có độ dài từ 10-15 trang. Sinh viên cần lựa chọn kiến trúc phần mềm cho ứng dụng của mình như: kiến trúc ba lớp MVC, MVP, SOA, Microser- vice, v.v. rồi giải thích sơ bộ về kiến trúc đó (không giải thích chi tiết/dài dòng). Sử dụng kiến trúc phần mềm đã chọn ở trên, sinh viên mô tả kiến trúc cụ thể cho ứng dụng của mình.


## Thiết kế kiến trúc….



### Thiết kế tổng quan…..


Sinh viên vẽ biểu đồ gói UML (UML package diagram), nêu rõ sự phụ thuộc giữa các gói (package). SV cần vẽ các gói sao cho chúng được phân theo các tầng rõ ràng, không được sắp đặt package lộn xộn trong hình vẽ. Sinh viên chú ý các quy tắc thiết kế (Các gói không phụ thuộc lẫn nhau, gói tầng dưới không phụ thuộc gói tầng trên, không phụ thuộc bỏ qua tầng, v.v.) và cần giải thích sơ lược về mục đích/nhiệm vụ của từng package.

Hình 3.1. Thiết kế tổng quát gói……….


### Thiết kế chi tiết các gói.


Hình 3.2. Thiết kế chi tiết gói………..


## Thiết kế giao diện


Phần này có độ dài từ hai đến ba trang. Sinh viên đặc tả thông tin về màn hình mà ứng dụng của mình hướng tới, bao gồm độ phân giải màn hình, kích thước màn hình, số lượng màu sắc hỗ trợ, v.v.

Tiếp đến, sinh viên đưa ra các thống nhất/chuẩn hóa của mình khi thiết kế giao diện như thiết kế nút, điều khiển, vị trí hiển thị thông điệp phản hồi, phối màu, v.v.

sinh viên sử dụng Figma, Canva….đưa ra một số hình ảnh minh họa thiết kế giao diện cho các chức năng quan trọng nhất. Lưu ý, sinh viên không nhầm lẫn giao diện thiết kế với giao diện của sản phẩm sau cùng,

Hình 3.3. Thiết kế giao diện trang chủ

Hình 3.4. Thiết kế giao diện Mobile……..


## Thiết kế lớp


Sinh viên trình bày thiết kế chi tiết các thuộc tính và phương thức cho một số lớp chủ đạo/quan trọng nhất của ứng dụng (từ 2-4 lớp). Thiết kế chi tiết cho các lớp khác, nếu muốn trình bày, sinh viên đưa vào phần phụ lục. Để minh họa thiết kế lớp, sinh viên thiết kế luồng truyền thông điệp giữa các đối tượng tham gia cho 2 đến 3 use case quan trọng nào đó bằng biểu đồ trình tự (hoặc biểu đồ giao tiếp).


### Một số biểu đồ lớp


Bảng  3.1. Lớp ….

Bảng  3.2. Lớp …..

Bảng  3.3. …..


### Sơ đồ lớp


Hình 3.5. Sơ đồ lớp……


## Thiết kế cơ sở dữ liệu


Phần này có độ dài từ hai đến bốn trang. Sinh viên thiết kế, vẽ và giải thích biểu đồ thực thể liên kết (E-R diagram). Từ đó, sinh viên thiết kế cơ sở dữ liệu tùy theo hệ quản trị cơ sở dữ liệu mà mình sử dụng (SQL, NoSQL, Firebase, v.v


### Danh sách bảng


Bảng  3.4. Bảng…..

Bảng  3.5. Bảng…..

Bảng  3.6. Bảng…..

Bảng  3.7. Bảng…..


### Biểu đồ thực thể liên kết


Hình 3.6. E-R diagram ……..


# XÂY DỰNG ỨNG DỤNG



## Các công cụ, ngôn ngữ nền tảng


Sinh viên liệt kê các công cụ, ngôn ngữ lập trình, API, thư viện, IDE, công cụ kiểm thử, v.v. mà mình sử dụng để phát triển ứng dụng. Mỗi công cụ phải được chỉ rõ phiên bản sử dụng.


## Kết quả đạt được


Hình 4.1. Giao diện trang chủ

Hình 4.2. Giao diện………..


## Kiểm thử ứng dụng


Sinh viên thiết kế các trường hợp kiểm thử cho hai đến ba chức năng quan trọng nhất. Sinh viên cần chỉ rõ các kỹ thuật kiểm thử đã sử dụng. Chi tiết các trường hợp kiểm thử khác, nếu muốn trình bày, sinh viên đưa vào phần phụ lục. Sinh viên sau cùng tổng kết về số lượng các trường hợp kiểm thử và kết quả kiểm thử. Sinh viên cần phân tích lý do nếu kết quả kiểm thử không đạ

Bảng  4.1. Kiểm thử chức năng …………

Bảng  4.2. Kiểm thử chức năng ………….

Bảng  4.3. Kiểm thử chức năng ………….


# KẾT LUẬN


Phần  này sinh viên viết tối đa 2 trang

Kết luận

Trong quá trình thực hiện đồ án, em đã nghiên cứu và áp dụng các kiến thức đã học

Hướng phát triển

Bổ sung chức năng tư vấn trực tuyến với dược sĩ nhằm hỗ trợ người dùng trong quá trình lựa chọn thuốc.

Ứng dụng trí tuệ nhân tạo (AI) để gợi ý sản phẩm phù hợp dựa trên nhu cầu và tình


# PHỤ LỤC



# TÀI LIỆU THAM KHẢO VÀ TRÍCH DẪN


ThS. Lê Trọng Nhất (2026). Bài giảng Học phần Phân tích và Đặc tả Yêu cầu Phần mềm. Khoa CNTT & Chuyển đổi số, Trường Đại học Nguyễn Trãi..

Martin Fowler. UML Distilled: A Brief Guide to the Standard Object Modeling Language (3rd Edition). Addison-Wesley Professional. 2003

VMware, Inc., “Spring Boot Documentation.” [Online]. Available: https://docs.spring.io/spring-boot/documentation.html. [Accessed: Oct. 11, 2026]

VMware, Inc., “Spring Boot: Building an Application with Spring Boot.” [Online]. Available: https://spring.io/guides/gs/spring-boot. [Accessed: Oct. 11, 2026]

IEEE Computer Society, Guide to the Software Engineering Body of Knowledge (SWEBOK). Available: https://www.computer.org/education/bodies-of-knowledge/software-engineering Ngày truy cập các tài liệu trực tuyến: 11/10/2026