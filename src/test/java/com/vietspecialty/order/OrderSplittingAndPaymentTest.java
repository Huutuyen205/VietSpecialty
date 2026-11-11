package com.vietspecialty.order;

import com.vietspecialty.domain.entity.*;
import com.vietspecialty.domain.enums.*;
import com.vietspecialty.dto.request.CartItemRequest;
import com.vietspecialty.dto.request.CheckoutFormRequest;
import com.vietspecialty.dto.response.CheckoutPreviewResponse;
import com.vietspecialty.repository.*;
import com.vietspecialty.service.CartService;
import com.vietspecialty.service.OrderService;
import com.vietspecialty.service.VnpayService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class OrderSplittingAndPaymentTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private VnpayService vnpayService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProvinceRepository provinceRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderGroupRepository orderGroupRepository;

    @Test
    @DisplayName("Đặt hàng tách 2 shop: Trừ kho nguyên tử, tạo 1 OrderGroup và 2 Order con, xóa giỏ hàng")
    void testPlaceOrderMultiVendorSplitting() {
        Province province = provinceRepository.findAll().get(0);
        Category category = categoryRepository.findAll().get(0);

        // 1. Tạo 2 shop khác nhau
        User seller1 = userRepository.save(User.builder().fullName("Seller 1").email("s1@vietspecialty.vn").passwordHash("h").phone("0911").role(Role.SELLER).status(UserStatus.ACTIVE).build());
        Shop shop1 = shopRepository.save(Shop.builder().name("HTX Trà 1").slug("htx-tra-1").owner(seller1).province(province).status(ShopStatus.APPROVED).build());

        User seller2 = userRepository.save(User.builder().fullName("Seller 2").email("s2@vietspecialty.vn").passwordHash("h").phone("0922").role(Role.SELLER).status(UserStatus.ACTIVE).build());
        Shop shop2 = shopRepository.save(Shop.builder().name("HTX Nấm 2").slug("htx-nam-2").owner(seller2).province(province).status(ShopStatus.APPROVED).build());

        // 2. Tạo 2 sản phẩm cho 2 shop
        Product p1 = productRepository.save(Product.builder().shop(shop1).category(category).province(province).name("Trà Xanh").slug("tra-xanh").price(new BigDecimal("100000")).stock(50).unit("Hộp").status(ProductStatus.ACTIVE).build());
        Product p2 = productRepository.save(Product.builder().shop(shop2).category(category).province(province).name("Nấm Hương").slug("nam-huong").price(new BigDecimal("150000")).stock(30).unit("Túi").status(ProductStatus.ACTIVE).build());

        // 3. Tạo người mua & địa chỉ nhận hàng
        User buyer = userRepository.save(User.builder().fullName("Khách Mua").email("buyer_split@vietspecialty.vn").passwordHash("h").phone("0933").role(Role.USER).status(UserStatus.ACTIVE).build());
        Address addr = addressRepository.save(Address.builder().user(buyer).receiverName("Khách Mua").receiverPhone("0933").province(province).ward("Xã B").streetAddress("123 Thôn C").isDefault(true).build());

        // 4. Cho sản phẩm của cả 2 shop vào giỏ
        cartService.addToCart(buyer.getId(), new CartItemRequest(p1.getId(), 2));
        cartService.addToCart(buyer.getId(), new CartItemRequest(p2.getId(), 1));

        // 5. Xem trước đơn hàng
        CheckoutPreviewResponse preview = orderService.previewCheckout(buyer.getId(), "STANDARD", null, null);
        assertNotNull(preview);
        assertEquals(2, preview.shopGroups().size(), "Đơn hàng phải được chia thành 2 nhóm shop");
        assertNotNull(preview.checkoutToken());

        // 6. Đặt hàng
        CheckoutFormRequest checkoutReq = new CheckoutFormRequest(
                addr.getId(), "COD", "STANDARD", null, null, preview.checkoutToken(), "Giao nhanh giúp tôi"
        );

        OrderGroup orderGroup = orderService.placeOrder(buyer.getId(), checkoutReq, "127.0.0.1");
        assertNotNull(orderGroup);
        assertNotNull(orderGroup.getCode());

        // Kiểm tra số lượng tồn kho đã giảm
        Product reloadedP1 = productRepository.findById(p1.getId()).orElseThrow();
        Product reloadedP2 = productRepository.findById(p2.getId()).orElseThrow();
        assertEquals(48, reloadedP1.getStock(), "Kho p1 phải giảm từ 50 xuống 48");
        assertEquals(29, reloadedP2.getStock(), "Kho p2 phải giảm từ 30 xuống 29");

        // Kiểm tra tách thành 2 đơn con
        List<Order> subOrders = orderRepository.findByOrderGroupId(orderGroup.getId());
        assertEquals(2, subOrders.size(), "Hệ thống phải tạo đúng 2 Order con cho 2 shop");

        // Kiểm tra giỏ hàng của người mua đã trống
        assertEquals(0, cartService.getCartItemCount(buyer.getId()));
    }
}
