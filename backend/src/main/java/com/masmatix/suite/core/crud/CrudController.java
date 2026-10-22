package com.masmatix.suite.core.crud;

import com.masmatix.suite.core.audit.AuditService;
import com.masmatix.suite.core.export.CsvWriter;
import com.masmatix.suite.core.web.NotFoundException;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * List, read, create, update, delete and CSV export for one entity type.
 * Every system module (hand-written or generated) extends this, so the
 * behaviour, role rules and audit trail are identical across the suite.
 */
public abstract class CrudController<T extends BaseEntity> {

    private static final String[] PROTECTED_FIELDS = {"id", "createdAt", "updatedAt"};

    protected final JpaRepository<T, Long> repository;
    protected final AuditService audit;
    private final String entityName;

    protected CrudController(JpaRepository<T, Long> repository, AuditService audit, String entityName) {
        this.repository = repository;
        this.audit = audit;
        this.entityName = entityName;
    }

    @GetMapping
    public Page<T> list(@RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "50") int size) {
        int safeSize = Math.min(Math.max(size, 1), 200);
        return repository.findAll(PageRequest.of(Math.max(page, 0), safeSize, Sort.by("id")));
    }

    @GetMapping("/{id}")
    public T get(@PathVariable Long id) {
        return find(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public T create(@Valid @RequestBody T body) {
        body.setId(null);
        T saved = repository.save(body);
        audit.record("CREATE", entityName, saved.getId(), null);
        return saved;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public T update(@PathVariable Long id, @Valid @RequestBody T body) {
        T existing = find(id);
        BeanUtils.copyProperties(body, existing, PROTECTED_FIELDS);
        T saved = repository.save(existing);
        audit.record("UPDATE", entityName, id, null);
        return saved;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        repository.delete(find(id));
        audit.record("DELETE", entityName, id, null);
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    public ResponseEntity<byte[]> exportCsv() {
        List<T> rows = repository.findAll(Sort.by("id"));
        byte[] csv = CsvWriter.fromBeans(rows).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + entityName + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }

    protected T find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException(entityName + " " + id + " not found"));
    }
}
