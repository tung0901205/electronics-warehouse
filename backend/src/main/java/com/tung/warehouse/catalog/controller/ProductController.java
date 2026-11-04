package com.tung.warehouse.catalog.controller;

import com.tung.warehouse.catalog.dto.ProductRequest;
import com.tung.warehouse.catalog.dto.ProductResponse;
import com.tung.warehouse.catalog.dto.VariantRequest;
import com.tung.warehouse.catalog.dto.VariantResponse;
import com.tung.warehouse.catalog.entity.CatalogStatus;
import com.tung.warehouse.catalog.service.ProductService;
import com.tung.warehouse.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Xem (GET): mọi người đã đăng nhập (UC2.5).
 * Thêm, sửa, đổi trạng thái sản phẩm và biến thể: từ Quản lý kho trở lên (UC2.3).
 */
@RestController
@RequestMapping("/api/products")
@Tag(name = "Sản phẩm và biến thể")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // ==================== SẢN PHẨM ====================

    @GetMapping
    @Operation(summary = "Tìm kiếm sản phẩm (lọc theo từ khóa, danh mục, trạng thái; có phân trang)")
    public PageResponse<ProductResponse> search(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "status", required = false) CatalogStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return productService.search(keyword, categoryId, status, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết sản phẩm kèm các biến thể")
    public ProductResponse getById(@PathVariable Long id) {
        return productService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    @Operation(summary = "Thêm sản phẩm")
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return productService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    @Operation(summary = "Sửa sản phẩm")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    @Operation(summary = "Kinh doanh lại sản phẩm")
    public ProductResponse activate(@PathVariable Long id) {
        return productService.setActive(id, true);
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    @Operation(summary = "Ngừng kinh doanh sản phẩm")
    public ProductResponse deactivate(@PathVariable Long id) {
        return productService.setActive(id, false);
    }

    // ==================== BIẾN THỂ ====================

    @PostMapping("/{productId}/variants")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    @Operation(summary = "Thêm biến thể (SKU) cho sản phẩm")
    public VariantResponse addVariant(@PathVariable Long productId, @Valid @RequestBody VariantRequest request) {
        return productService.addVariant(productId, request);
    }

    @PutMapping("/{productId}/variants/{variantId}")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    @Operation(summary = "Sửa biến thể (không đổi được SKU và kiểu theo dõi)")
    public VariantResponse updateVariant(@PathVariable Long productId, @PathVariable Long variantId,
                                         @Valid @RequestBody VariantRequest request) {
        return productService.updateVariant(productId, variantId, request);
    }

    @PatchMapping("/{productId}/variants/{variantId}/activate")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    @Operation(summary = "Kinh doanh lại biến thể")
    public VariantResponse activateVariant(@PathVariable Long productId, @PathVariable Long variantId) {
        return productService.setVariantActive(productId, variantId, true);
    }

    @PatchMapping("/{productId}/variants/{variantId}/deactivate")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    @Operation(summary = "Ngừng kinh doanh biến thể")
    public VariantResponse deactivateVariant(@PathVariable Long productId, @PathVariable Long variantId) {
        return productService.setVariantActive(productId, variantId, false);
    }
}
