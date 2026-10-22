package com.masmatix.suite.core.export;

import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.BeanUtils;

/** Writes simple beans to RFC 4180 CSV using their readable properties. */
public final class CsvWriter {

    private CsvWriter() {}

    public static String fromBeans(List<?> rows) {
        if (rows.isEmpty()) {
            return "";
        }
        List<PropertyDescriptor> props = new ArrayList<>(Arrays.stream(
                        BeanUtils.getPropertyDescriptors(rows.get(0).getClass()))
                .filter(p -> p.getReadMethod() != null && !"class".equals(p.getName()))
                .filter(p -> BeanUtils.isSimpleValueType(p.getPropertyType()))
                .toList());
        props.sort(Comparator.comparing((PropertyDescriptor p) -> "id".equals(p.getName()) ? "" : p.getName()));

        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", props.stream().map(PropertyDescriptor::getName).toList())).append("\r\n");
        for (Object row : rows) {
            List<String> cells = new ArrayList<>();
            for (PropertyDescriptor p : props) {
                try {
                    Object v = p.getReadMethod().invoke(row);
                    cells.add(escape(v == null ? "" : v.toString()));
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
            sb.append(String.join(",", cells)).append("\r\n");
        }
        return sb.toString();
    }

    static String escape(String value) {
        // A leading = + - @ would run as a formula in Excel; prefix with a quote.
        String v = value;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0 && !v.matches("-?\\d+(\\.\\d+)?")) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
