package com.masmatix.suite.pos;

import com.masmatix.suite.core.audit.AuditService;
import com.masmatix.suite.core.crud.CrudController;
import com.masmatix.suite.core.web.NotFoundException;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pos/products")
public class ProductController extends CrudController<Product> {

    private final ProductRepository products;

    public ProductController(ProductRepository products, AuditService audit) {
        super(products, audit, "product");
        this.products = products;
    }

    /** Called by the till when a barcode scanner "types" a code and presses Enter. */
    @GetMapping("/barcode/{code}")
    public Product byBarcode(@PathVariable String code) {
        return products.findByBarcode(code.trim())
                .filter(Product::isActive)
                .orElseThrow(() -> new NotFoundException("No active product with barcode " + code));
    }

    @GetMapping("/low-stock")
    public List<Product> lowStock() {
        return products.findLowStock();
    }
}
