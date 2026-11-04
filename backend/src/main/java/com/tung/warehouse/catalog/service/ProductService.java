package com.tung.warehouse.catalog.service;

import com.tung.warehouse.catalog.dto.ProductRequest;
import com.tung.warehouse.catalog.dto.ProductResponse;
import com.tung.warehouse.catalog.dto.VariantRequest;
import com.tung.warehouse.catalog.dto.VariantResponse;
import com.tung.warehouse.catalog.entity.Brand;
import com.tung.warehouse.catalog.entity.CatalogStatus;
import com.tung.warehouse.catalog.entity.Category;
import com.tung.warehouse.catalog.entity.Product;
import com.tung.warehouse.catalog.entity.ProductVariant;
import com.tung.warehouse.catalog.entity.Unit;
import com.tung.warehouse.catalog.repository.BrandRepository;
import com.tung.warehouse.catalog.repository.CategoryRepository;
import com.tung.warehouse.catalog.repository.ProductRepository;
import com.tung.warehouse.catalog.repository.ProductSpecs;
import com.tung.warehouse.catalog.repository.ProductVariantRepository;
import com.tung.warehouse.catalog.repository.UnitRepository;
import com.tung.warehouse.common.exception.ConflictException;
import com.tung.warehouse.common.exception.NotFoundException;
import com.tung.warehouse.common.response.PageResponse;
import com.tung.warehouse.common.util.PagingUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nghiệp vụ UC2.3 Quản lý sản phẩm và biến thể, UC2.5 tra cứu. */
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final UnitRepository unitRepository;

    public ProductService(ProductRepository productRepository, ProductVariantRepository variantRepository,
                          CategoryRepository categoryRepository, BrandRepository brandRepository,
                          UnitRepository unitRepository) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.unitRepository = unitRepository;
    }

    // ==================== SẢN PHẨM ====================

    /** Tìm kiếm với 3 điều kiện tùy chọn, ghép bằng where(...).and(...).and(...). Điều kiện nào rỗng (null) thì tự bỏ qua. */
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String keyword, Long categoryId, CatalogStatus status, int page, int size) {
        Specification<Product> spec = Specification.where(ProductSpecs.nameContains(keyword))
                .and(ProductSpecs.hasCategory(categoryId))
                .and(ProductSpecs.hasStatus(status));

        Page<Product> result = productRepository.findAll(spec, PagingUtil.of(page, size));
        return PageResponse.from(result.map(ProductResponse::from));
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        return ProductResponse.from(findProductOrThrow(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Category category = findActiveCategory(request.categoryId());
        Brand brand = findBrand(request.brandId());
        Product product = new Product(request.name().trim(), category, brand, request.description());
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = findProductOrThrow(id);
        Category category = findActiveCategory(request.categoryId());
        Brand brand = findBrand(request.brandId());
        product.update(request.name().trim(), category, brand, request.description());
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse setActive(Long id, boolean active) {
        Product product = findProductOrThrow(id);
        if (active) {
            product.activate();
        } else {
            product.deactivate();
        }
        return ProductResponse.from(product);
    }

    // ==================== BIẾN THỂ ====================

    /** [MỚI Giai đoạn 3] Cho module khác (inventory) hỏi thông tin một biến thể: kiểu theo dõi, trạng thái... */
    @Transactional(readOnly = true)
    public VariantResponse getVariant(Long variantId) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new NotFoundException("Biến thể", variantId));
        return VariantResponse.from(variant);
    }

    @Transactional
    public VariantResponse addVariant(Long productId, VariantRequest request) {
        Product product = findProductOrThrow(productId);
        if (product.getStatus() == CatalogStatus.INACTIVE) {
            throw new ConflictException("Sản phẩm đã ngừng kinh doanh, không thêm được biến thể");
        }

        String sku = normalizeSku(request.sku());
        String barcode = normalizeBarcode(request.barcode());
        if (variantRepository.existsBySku(sku)) {
            throw new ConflictException("SKU đã tồn tại: " + sku);
        }
        if (barcode != null && variantRepository.existsByBarcode(barcode)) {
            throw new ConflictException("Barcode đã tồn tại: " + barcode);
        }

        Unit unit = findUnit(request.unitId());
        ProductVariant variant = new ProductVariant(product, sku, barcode, request.attributes(), unit,
                request.trackingType(), request.listPrice(), request.warrantyMonths());
        return VariantResponse.from(variantRepository.save(variant));
    }

    @Transactional
    public VariantResponse updateVariant(Long productId, Long variantId, VariantRequest request) {
        ProductVariant variant = findVariantOrThrow(productId, variantId);

        // SKU và kiểu theo dõi gắn với tồn kho và lịch sử nên không cho đổi sau khi đã tạo
        if (!variant.getSku().equals(normalizeSku(request.sku()))) {
            throw new ConflictException("Không thể đổi SKU sau khi đã tạo");
        }
        if (variant.getTrackingType() != request.trackingType()) {
            throw new ConflictException("Không thể đổi kiểu theo dõi (SERIAL/QUANTITY) sau khi đã tạo");
        }

        String barcode = normalizeBarcode(request.barcode());
        if (barcode != null && variantRepository.existsByBarcodeAndIdNot(barcode, variantId)) {
            throw new ConflictException("Barcode đã tồn tại: " + barcode);
        }

        Unit unit = findUnit(request.unitId());
        variant.updateDetails(barcode, request.attributes(), unit, request.listPrice(), request.warrantyMonths());
        return VariantResponse.from(variant);
    }

    @Transactional
    public VariantResponse setVariantActive(Long productId, Long variantId, boolean active) {
        ProductVariant variant = findVariantOrThrow(productId, variantId);
        if (active) {
            variant.activate();
        } else {
            variant.deactivate();
        }
        return VariantResponse.from(variant);
    }

    // ==================== HÀM HỖ TRỢ ====================

    private Product findProductOrThrow(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new NotFoundException("Sản phẩm", id));
    }

    /** Lấy biến thể và kiểm tra nó thuộc đúng sản phẩm trên URL, tránh sửa nhầm biến thể của sản phẩm khác. */
    private ProductVariant findVariantOrThrow(Long productId, Long variantId) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new NotFoundException("Biến thể", variantId));
        if (!variant.getProduct().getId().equals(productId)) {
            throw new NotFoundException("Biến thể", variantId);
        }
        return variant;
    }

    private Category findActiveCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Danh mục", categoryId));
        if (category.getStatus() == CatalogStatus.INACTIVE) {
            throw new ConflictException("Danh mục đã ngừng hoạt động");
        }
        return category;
    }

    private Brand findBrand(Long brandId) {
        if (brandId == null) {
            return null;
        }
        return brandRepository.findById(brandId).orElseThrow(() -> new NotFoundException("Thương hiệu", brandId));
    }

    private Unit findUnit(Long unitId) {
        return unitRepository.findById(unitId).orElseThrow(() -> new NotFoundException("Đơn vị tính", unitId));
    }

    private static String normalizeSku(String sku) {
        return sku.trim().toUpperCase();
    }

    /** Barcode để trống thì lưu NULL (không lưu chuỗi rỗng), vì cột barcode là UNIQUE. */
    private static String normalizeBarcode(String barcode) {
        if (barcode == null || barcode.isBlank()) {
            return null;
        }
        return barcode.trim();
    }
}
