package com.billngshop.BILLING;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.billngshop.BILLING.entity.Product;
import com.billngshop.BILLING.entity.Supplier;
import com.billngshop.BILLING.entity.SalesInvoice;
import com.billngshop.BILLING.repository.AuditEventRepository;
import com.billngshop.BILLING.repository.PurchaseOrderRepository;
import com.billngshop.BILLING.repository.ProductRepository;
import com.billngshop.BILLING.repository.RefundRecordRepository;
import com.billngshop.BILLING.repository.SalesInvoiceRepository;
import com.billngshop.BILLING.repository.SupplierRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.bootstrap-admin.username=test-admin",
        "app.bootstrap-admin.email=test-admin@example.test",
        "app.security.maximum-concurrent-sessions=0",
        "app.bootstrap-admin.password=TestPassword123!"
})
class RetailApiTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository products;

    @Autowired
    private SalesInvoiceRepository invoices;

    @Autowired
    private AuditEventRepository auditEvents;

    @Autowired
    private PurchaseOrderRepository purchases;

    @Autowired
    private SupplierRepository suppliers;

    @Autowired
    private RefundRecordRepository refunds;

    @BeforeEach
    void cleanData() {
        purchases.deleteAll();
        refunds.deleteAll();
        invoices.deleteAll();
        products.deleteAll();
        suppliers.deleteAll();
        auditEvents.deleteAll();
    }

    @Test
    void loginEstablishesRoleBasedSession() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"test-admin","password":"TestPassword123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("ADMIN")))
                .andExpect(jsonPath("$.home", is("/api/dashboard")))
                .andReturn();

        mockMvc.perform(get("/api/auth/me").session((org.springframework.mock.web.MockHttpSession) result.getRequest()
                        .getSession(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("test-admin")));
    }

    @Test
    void completedInvoiceDeductsStockAndRecordsSale() throws Exception {
        Product product = createProduct(2, "12.50");
        mockMvc.perform(post("/api/invoices").with(user("cashier").roles("BILLING_STAFF")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lines":[{"productId":%d,"quantity":1}],
                                 "payments":[{"mode":"cash","amount":12.50}]}
                                """.formatted(product.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.total", is(12.5)));

        org.assertj.core.api.Assertions.assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity())
                .isEqualTo(1);
        Long invoiceId = invoices.findAll().get(0).getId();
        mockMvc.perform(get("/api/invoices/{id}", invoiceId)
                        .with(user("cashier").roles("BILLING_STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].productName", is("Test item")));
        MvcResult pdf = mockMvc.perform(get("/api/invoices/{id}/print", invoiceId)
                        .with(user("cashier").roles("BILLING_STAFF")))
                .andExpect(status().isOk())
                .andReturn();
        org.assertj.core.api.Assertions.assertThat(pdf.getResponse().getContentAsByteArray())
                .startsWith("%PDF-1.4".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
    }

    @Test
    void saleIsRejectedWhenItWouldCreateNegativeStock() throws Exception {
        Product product = createProduct(0, "9.99");
        mockMvc.perform(post("/api/invoices").with(user("cashier").roles("BILLING_STAFF")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lines":[{"productId":%d,"quantity":1}],
                                 "payments":[{"mode":"cash","amount":9.99}]}
                                """.formatted(product.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Insufficient stock")));

        org.assertj.core.api.Assertions.assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity())
                .isZero();
        org.assertj.core.api.Assertions.assertThat(invoices.count()).isZero();
    }

    @Test
    void registrationCreatesInactiveAccountUntilAdminActivation() throws Exception {
        mockMvc.perform(post("/api/users").with(user("test-admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"new-cashier","email":"cashier@example.test",
                         "password":"StrongPassword123!","role":"BILLING_STAFF"}
                        """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.active", is(false)));
    }

    @Test
    void publicRegistrationCreatesInactiveBillingStaffWithoutElevatedRole() throws Exception {
        mockMvc.perform(post("/api/users").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"self-signup","email":"signup@example.test",
                                 "password":"StrongPassword123!","firstName":"Sam",
                                 "lastName":"Taylor","mobileNumber":"+1 555 0100","role":"ADMIN"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role", is("BILLING_STAFF")))
                .andExpect(jsonPath("$.active", is(false)))
                .andExpect(jsonPath("$.firstName", is("Sam")))
                .andExpect(jsonPath("$.lastName", is("Taylor")))
                .andExpect(jsonPath("$.mobileNumber", is("+1 555 0100")));
    }

    @Test
    void loginAcceptsEmailAsIdentifier() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"test-admin@example.test","password":"TestPassword123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("ADMIN")))
                .andExpect(jsonPath("$.home", is("/api/dashboard")));
    }

    @Test
    void purchaseReceiptsIncreaseStockOnlyWhenGoodsArrive() throws Exception {
        Supplier supplier = new Supplier();
        supplier.setName("Supplier One");
        supplier.setContact("555-0100");
        supplier = suppliers.save(supplier);
        Product product = createProduct(0, "20.00");

        mockMvc.perform(post("/api/purchases").with(user("stock-user").roles("INVENTORY_STAFF")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"supplierId":%d,"lines":[{"productId":%d,"quantity":2,"unitCost":600.00}]}
                        """.formatted(supplier.getId(), product.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("PENDING_APPROVAL")));

        long purchaseId = purchases.findAll().get(0).getId();
        mockMvc.perform(post("/api/purchases/{id}/approve", purchaseId)
                .with(user("manager").roles("STORE_MANAGER")).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("APPROVED")));
        mockMvc.perform(post("/api/purchases/{id}/receive", purchaseId)
                .with(user("stock-user").roles("INVENTORY_STAFF")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        [{"productId":%d,"quantity":1}]
                        """.formatted(product.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("PARTIALLY_RECEIVED")));

        org.assertj.core.api.Assertions.assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity())
        .isEqualTo(1);
    }

    @Test
    void partialReturnRestoresStockAndCreatesRefund() throws Exception {
        Product product = createProduct(2, "12.50");
        mockMvc.perform(post("/api/invoices").with(user("cashier").roles("BILLING_STAFF")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"lines":[{"productId":%d,"quantity":2}],
                         "payments":[{"mode":"cash","amount":25.00}]}
                        """.formatted(product.getId())))
        .andExpect(status().isOk());
        SalesInvoice savedInvoice = invoices.findAll().get(0);

        mockMvc.perform(post("/api/invoices/{id}/returns", savedInvoice.getId())
                .with(user("cashier").roles("BILLING_STAFF")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"lines":[{"productId":%d,"quantity":1}],
                         "refundMode":"cash","reason":"Damaged item"}
                        """.formatted(product.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("COMPLETED")));

        org.assertj.core.api.Assertions.assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity())
        .isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(refunds.count()).isEqualTo(1);
    }

    private Product createProduct(int stock, String price) {
        Product product = new Product();
        product.setSku("SKU-" + stock + "-" + price.replace(".", ""));
        product.setName("Test item");
        product.setSellingPrice(new BigDecimal(price));
        product.setCostPrice(new BigDecimal("5.00"));
        product.setStockQuantity(stock);
        product.setReorderLevel(0);
        return products.save(product);
    }
}
