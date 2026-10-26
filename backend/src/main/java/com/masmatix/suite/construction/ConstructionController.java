package com.masmatix.suite.construction;

import com.masmatix.suite.generated.construction.LabourDay;
import com.masmatix.suite.generated.construction.LabourDayRepository;
import com.masmatix.suite.generated.construction.SiteMaterial;
import com.masmatix.suite.generated.construction.SiteMaterialRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Cost dashboard: material and labour spend per site. */
@RestController
@RequestMapping("/api/construction/desk")
public class ConstructionController {

    public record SiteCost(String site, Map<String, BigDecimal> materialsByType, BigDecimal materials,
                           int labourDays, int workerDays, BigDecimal labour, BigDecimal total) {}

    private final SiteMaterialRepository materials;
    private final LabourDayRepository labour;

    public ConstructionController(SiteMaterialRepository materials, LabourDayRepository labour) {
        this.materials = materials;
        this.labour = labour;
    }

    @GetMapping("/costs")
    @Transactional(readOnly = true)
    public List<SiteCost> costs() {
        List<SiteMaterial> m = materials.findAll();
        List<LabourDay> l = labour.findAll();
        TreeSet<String> sites = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        m.forEach(x -> sites.add(x.getSite()));
        l.forEach(x -> sites.add(x.getSite()));
        return sites.stream().map(site -> {
            Map<String, BigDecimal> byType = new TreeMap<>();
            BigDecimal mat = BigDecimal.ZERO.setScale(2);
            for (SiteMaterial x : m) {
                if (x.getSite().equalsIgnoreCase(site)) {
                    BigDecimal c = ConstructionRules.materialCost(x.getQuantity(), x.getUnitCost());
                    byType.merge(x.getMaterial() == null ? "OTHER" : x.getMaterial(), c, BigDecimal::add);
                    mat = mat.add(c);
                }
            }
            BigDecimal lab = BigDecimal.ZERO.setScale(2);
            int days = 0;
            int workerDays = 0;
            for (LabourDay x : l) {
                if (x.getSite().equalsIgnoreCase(site)) {
                    lab = lab.add(ConstructionRules.labourCost(x.getWorkers(), x.getDailyRate()));
                    days++;
                    workerDays += x.getWorkers() == null ? 0 : x.getWorkers();
                }
            }
            return new SiteCost(site, byType, mat, days, workerDays, lab, mat.add(lab));
        }).toList();
    }
}
