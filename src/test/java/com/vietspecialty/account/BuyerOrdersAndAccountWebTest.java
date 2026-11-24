package com.vietspecialty.account;

import com.vietspecialty.domain.entity.*;
import com.vietspecialty.domain.enums.*;
import com.vietspecialty.dto.request.AddressRequest;
import com.vietspecialty.dto.request.CartItemRequest;
import com.vietspecialty.dto.request.CheckoutFormRequest;
import com.vietspecialty.dto.response.AddressResponse;
import com.vietspecialty.dto.response.CheckoutPreviewResponse;
import com.vietspecialty.repository.*;
import com.vietspecialty.security.CustomUserDetails;
import com.vietspecialty.service.AddressService;
import com.vietspecialty.service.CartService;
import com.vietspecialty.service.OrderService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class BuyerOrdersAndAccountWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProvinceRepository provinceRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ChatConversationRepository chatConversationRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private AddressService addressService;

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    private User createFreshUser(String prefix) {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder()
                .fullName("User " + prefix)
                .email(prefix + "_" + uid + "@vietspecialty.vn")
                .passwordHash(passwordEncoder.encode("Password123@"))
                .phone("0912345678")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build());
    }

    private Order createCodOrderForUser(User buyer) {
        Province province = provinceRepository.findAll().get(0);
        Address addr = addressRepository.save(Address.builder()
                .user(buyer)
                .receiverName(buyer.getFullName())
                .receiverPhone("0912345678")
                .province(province)
                .ward("Phường Bến Nghé")
                .streetAddress("123 Đồng Khởi")
                .isDefault(true)
                .build());
        Product prod = productRepository.findAll().stream()
                .filter(p -> p.getStatus() == ProductStatus.ACTIVE && p.getStock() >= 10)
                .findFirst().orElseThrow();
        cartService.addToCart(buyer.getId(), new CartItemRequest(prod.getId(), 1));
        CheckoutPreviewResponse preview = orderService.previewCheckout(buyer.getId(), "STANDARD", null, null);
        OrderGroup group = orderService.placeOrder(buyer.getId(),
                new CheckoutFormRequest(addr.getId(), "COD", "STANDARD", null, null, preview.checkoutToken(), null),
                "127.0.0.1");
        return orderRepository.findByOrderGroupId(group.getId()).get(0);
    }

    @Test
    @DisplayName("/account/orders, /account/orders/{code}, reorder/refund-request IDOR và /api/orders/{id}/received kiểm tra quyền & trạng thái")
    void testBuyerOrdersAndIdorProtection() throws Exception {
        User owner = createFreshUser("ord_owner");
        User attacker = createFreshUser("ord_attacker");
        CustomUserDetails ownerDetails = new CustomUserDetails(owner);
        CustomUserDetails attackerDetails = new CustomUserDetails(attacker);

        Order order = createCodOrderForUser(owner);
        entityManager.flush();
        entityManager.clear();

        // Owner views /account/orders with status filter
        mockMvc.perform(get("/account/orders")
                        .with(user(ownerDetails))
                        .param("status", "PENDING_CONFIRM"))
                .andExpect(status().isOk())
                .andExpect(view().name("web/orders/index"))
                .andExpect(model().attribute("selectedStatus", "PENDING_CONFIRM"))
                .andExpect(content().string(containsString(order.getCode())));

        // Owner views /account/orders/{code}
        mockMvc.perform(get("/account/orders/" + order.getCode()).with(user(ownerDetails)))
                .andExpect(status().isOk())
                .andExpect(view().name("web/orders/detail"))
                .andExpect(content().string(containsString(order.getCode())));

        // Wrong buyer IDOR on /account/orders/{code} -> 400 error/500
        mockMvc.perform(get("/account/orders/" + order.getCode()).with(user(attackerDetails)))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/500"));

        // Wrong buyer IDOR on POST /orders/{id}/reorder -> 400 error/500, victim & attacker cart untouched
        mockMvc.perform(post("/orders/" + order.getId() + "/reorder")
                        .with(user(attackerDetails))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/500"));
        assertEquals(0, cartItemRepository.countByUserId(owner.getId()));
        assertEquals(0, cartItemRepository.countByUserId(attacker.getId()));

        // Wrong buyer IDOR on POST /orders/{id}/refund-request -> 400 error/500, paymentStatus untouched
        mockMvc.perform(post("/orders/" + order.getId() + "/refund-request")
                        .with(user(attackerDetails))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/500"));
        assertEquals(OrderPaymentStatus.UNPAID,
                orderRepository.findById(order.getId()).orElseThrow().getPaymentStatus());

        // POST /api/orders/{id}/received while PENDING_CONFIRM -> 400 JSON
        mockMvc.perform(post("/api/orders/" + order.getId() + "/received")
                        .with(user(ownerDetails))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // POST /api/orders/{id}/received by wrong buyer -> 403 JSON
        mockMvc.perform(post("/api/orders/" + order.getId() + "/received")
                        .with(user(attackerDetails))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /account/addresses: thêm hợp lệ, lỗi SĐT, giới hạn 10 địa chỉ và chống IDOR default/delete")
    void testAddressesFormLimitAndIdor() throws Exception {
        User owner = createFreshUser("addr_owner");
        User attacker = createFreshUser("addr_attacker");
        CustomUserDetails ownerDetails = new CustomUserDetails(owner);
        CustomUserDetails attackerDetails = new CustomUserDetails(attacker);
        Integer provinceId = provinceRepository.findAll().get(0).getId();

        // Valid form -> 302 /account/addresses + flash successMessage
        mockMvc.perform(post("/account/addresses")
                        .with(user(ownerDetails))
                        .with(csrf())
                        .param("receiverName", "Nguyễn Văn Chủ")
                        .param("receiverPhone", "0912345678")
                        .param("provinceId", String.valueOf(provinceId))
                        .param("ward", "Phường 1")
                        .param("streetAddress", "10 Đường ABC")
                        .param("isDefault", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/addresses"))
                .andExpect(flash().attributeExists("successMessage"));

        // Invalid phone -> 200 account/addresses
        mockMvc.perform(post("/account/addresses")
                        .with(user(ownerDetails))
                        .with(csrf())
                        .param("receiverName", "Nguyễn Văn Lỗi")
                        .param("receiverPhone", "0123")
                        .param("provinceId", String.valueOf(provinceId))
                        .param("ward", "Phường 1")
                        .param("streetAddress", "10 Đường ABC"))
                .andExpect(status().isOk())
                .andExpect(view().name("account/addresses"))
                .andExpect(model().attributeHasFieldErrors("addressRequest", "receiverPhone"));

        // Add 9 more addresses (total 10)
        for (int i = 2; i <= 10; i++) {
            addressService.createAddress(owner.getId(),
                    new AddressRequest("Người Nhận " + i, "0912345678", provinceId, "Phường " + i, "Số " + i, false));
        }
        List<AddressResponse> allAddrs = addressService.getUserAddresses(owner.getId());
        assertEquals(10, allAddrs.size());

        // 11th address -> 302 /account/addresses + flash errorMessage
        mockMvc.perform(post("/account/addresses")
                        .with(user(ownerDetails))
                        .with(csrf())
                        .param("receiverName", "Người Thứ 11")
                        .param("receiverPhone", "0912345678")
                        .param("provinceId", String.valueOf(provinceId))
                        .param("ward", "Phường 11")
                        .param("streetAddress", "Số 11"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/addresses"))
                .andExpect(flash().attributeExists("errorMessage"));
        assertEquals(10, addressRepository.countByUserId(owner.getId()));

        // Cross-user IDOR on /default and /delete
        Address victimAddr = addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(owner.getId()).stream()
                .filter(a -> !a.getIsDefault())
                .findFirst().orElseThrow();

        mockMvc.perform(post("/account/addresses/" + victimAddr.getId() + "/default")
                        .with(user(attackerDetails))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/addresses"))
                .andExpect(flash().attributeExists("errorMessage"));
        assertFalse(addressRepository.findById(victimAddr.getId()).orElseThrow().getIsDefault());

        mockMvc.perform(post("/account/addresses/" + victimAddr.getId() + "/delete")
                        .with(user(attackerDetails))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/addresses"))
                .andExpect(flash().attributeExists("errorMessage"));
        assertTrue(addressRepository.findById(victimAddr.getId()).isPresent());
    }

    @Test
    @DisplayName("POST /account/password xử lý đúng các ca lỗi: sai mật khẩu cũ, xác nhận không khớp, trùng mật khẩu cũ")
    void testChangePasswordNegativeCases() throws Exception {
        User u = createFreshUser("pwd_neg");
        CustomUserDetails details = new CustomUserDetails(u);

        // Wrong oldPassword -> 302 /account/password + flash errorMessage
        mockMvc.perform(post("/account/password")
                        .with(user(details))
                        .with(csrf())
                        .param("oldPassword", "WrongOld123@")
                        .param("newPassword", "NewPass789@")
                        .param("confirmNewPassword", "NewPass789@"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/password"))
                .andExpect(flash().attributeExists("errorMessage"));

        // Mismatch confirmNewPassword -> 200 account/password with field error
        mockMvc.perform(post("/account/password")
                        .with(user(details))
                        .with(csrf())
                        .param("oldPassword", "Password123@")
                        .param("newPassword", "NewPass789@")
                        .param("confirmNewPassword", "Different789@"))
                .andExpect(status().isOk())
                .andExpect(view().name("account/password"))
                .andExpect(model().attributeHasFieldErrors("passwordRequest", "confirmNewPassword"));

        // Same-as-old newPassword -> 302 /account/password + flash errorMessage
        mockMvc.perform(post("/account/password")
                        .with(user(details))
                        .with(csrf())
                        .param("oldPassword", "Password123@")
                        .param("newPassword", "Password123@")
                        .param("confirmNewPassword", "Password123@"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/password"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    @DisplayName("Wishlist (/wishlist/toggle param, JSON body, thiếu productId, /wishlist/clear) & Notification IDOR & Chat validation/history")
    void testWishlistNotificationIdorAndChat() throws Exception {
        User u1 = createFreshUser("misc_u1");
        User u2 = createFreshUser("misc_u2");
        CustomUserDetails d1 = new CustomUserDetails(u1);
        CustomUserDetails d2 = new CustomUserDetails(u2);

        Product p = productRepository.findAll().stream()
                .filter(pr -> pr.getStatus() == ProductStatus.ACTIVE)
                .findFirst().orElseThrow();

        // Wishlist: missing productId -> success=false
        mockMvc.perform(post("/wishlist/toggle")
                        .with(user(d1))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));

        // Wishlist: query param -> added=true
        mockMvc.perform(post("/wishlist/toggle")
                        .with(user(d1))
                        .with(csrf())
                        .param("productId", String.valueOf(p.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.added").value(true))
                .andExpect(jsonPath("$.count").value(1));

        // Wishlist: JSON body toggle off -> added=false, then toggle back on
        mockMvc.perform(post("/wishlist/toggle")
                        .with(user(d1))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + p.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.added").value(false))
                .andExpect(jsonPath("$.count").value(0));

        mockMvc.perform(post("/wishlist/toggle")
                        .with(user(d1))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + p.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(true));

        // Wishlist clear -> 302 /wishlist and empties list
        mockMvc.perform(post("/wishlist/clear")
                        .with(user(d1))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/wishlist"));

        mockMvc.perform(get("/wishlist").with(user(d1)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("wishlistIds", empty()));

        // Notifications: wrong user does not mutate owner's isRead=false; owner sets isRead=true
        Notification notif = notificationRepository.save(Notification.builder()
                .user(u1)
                .type("ORDER_NEW")
                .title("Test Notification")
                .content("Nội dung thông báo")
                .isRead(false)
                .build());
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/api/notifications/" + notif.getId() + "/read")
                        .with(user(d2))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        assertFalse(notificationRepository.findById(notif.getId()).orElseThrow().getIsRead(),
                "User khác không được đánh dấu đã đọc thông báo của nạn nhân");

        mockMvc.perform(post("/api/notifications/" + notif.getId() + "/read")
                        .with(user(d1))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        entityManager.flush();
        entityManager.clear();
        assertTrue(notificationRepository.findById(notif.getId()).orElseThrow().getIsRead());

        // Chat: blank message -> 400 JSON validation error
        mockMvc.perform(post("/api/chat")
                        .with(user(d1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors.message").exists());

        // Chat: authenticated user sends order lookup message -> persists user + retrieves history
        String chatKey = "chat_auth_" + UUID.randomUUID().toString().substring(0, 10);
        mockMvc.perform(post("/api/chat")
                        .with(user(d1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chatKey\":\"" + chatKey + "\",\"message\":\"đơn hàng của tôi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.chatKey").value(chatKey));

        entityManager.flush();
        entityManager.clear();

        ChatConversation conv = chatConversationRepository.findByChatKey(chatKey).orElseThrow();
        assertNotNull(conv.getUser());
        assertEquals(u1.getId(), conv.getUser().getId());

        MvcResult histRes = mockMvc.perform(get("/api/chat/history").param("chatKey", chatKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(2))))
                .andReturn();
        assertTrue(histRes.getResponse().getContentAsString().contains("đơn hàng của tôi"));
    }
}
