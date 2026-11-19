package com.zifang.z.dms.admin.web;

import com.zifang.z.dms.admin.auth.DmsCurrentUser;
import com.zifang.z.dms.admin.auth.DmsPermissionPoint;
import com.zifang.z.dms.admin.auth.DmsPermissionService;
import com.zifang.z.dms.admin.web.dto.ApiResult;
import com.zifang.z.dms.core.explorer.SchemaExplorer;
import com.zifang.z.dms.core.explorer.SchemaObject;
import com.zifang.z.dms.core.explorer.TableDataReader;
import com.zifang.z.dms.core.explorer.TableQuery;
import com.zifang.z.dms.core.explorer.TableFilter;
import com.zifang.z.dms.core.model.DataSourceDef;
import com.zifang.z.dms.core.service.DataSourceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据浏览（phpMyAdmin / Adminer 的「结构 + 内容」两屏的后端）。
 *
 * <p><b>权限口径</b>：
 * <ul>
 *   <li>结构（库 / 表 / 字段 / DDL）→ {@code DMS_SCHEMA_VIEW}；</li>
 *   <li>表数据分页 → {@code DMS_DATA_VIEW}；</li>
 *   <li>数据源候选清单 → {@code DMS_SCHEMA_VIEW}（DATA_VIEW 单权限的用户也要能选库浏览，
 *       所以这里返回<b>最小视图</b>：id / name / type，不带 credentialRef 与连接属性）。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/dms/explorer")
public class ExplorerController {

    private final DataSourceService dataSourceService;
    private final SchemaExplorer schemaExplorer;
    private final TableDataReader tableDataReader;
    private final DmsPermissionService permissionService;

    public ExplorerController(DataSourceService dataSourceService,
                              SchemaExplorer schemaExplorer,
                              TableDataReader tableDataReader,
                              DmsPermissionService permissionService) {
        this.dataSourceService = dataSourceService;
        this.schemaExplorer = schemaExplorer;
        this.tableDataReader = tableDataReader;
        this.permissionService = permissionService;
    }

    /** 可浏览的数据源清单（最小视图：id / name / type，没有凭据引用与连接属性）。 */
    @GetMapping("/sources")
    public ApiResult<List<Map<String, Object>>> sources(HttpServletRequest request) {
        permissionService.require(currentUser(request), DmsPermissionPoint.DMS_SCHEMA_VIEW);
        List<Map<String, Object>> out = new ArrayList<>();
        for (DataSourceDef def : dataSourceService.list()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", def.getId());
            m.put("name", def.getName());
            m.put("type", def.getType() == null ? null : def.getType().name());
            out.add(m);
        }
        return ApiResult.ok(out);
    }

    /** 某数据源的库清单。 */
    @GetMapping("/{id}/schemas")
    public ApiResult<List<SchemaObject>> schemas(@PathVariable("id") String id,
                                                 HttpServletRequest request) {
        permissionService.require(currentUser(request), DmsPermissionPoint.DMS_SCHEMA_VIEW);
        requireRegistered(id);
        return ApiResult.ok(schemaExplorer.listSchemas(id));
    }

    /** 某库下的表 / 视图清单。 */
    @GetMapping("/{id}/tables")
    public ApiResult<List<SchemaObject>> tables(@PathVariable("id") String id,
                                                @RequestParam(value = "schema", required = false) String schema,
                                                @RequestParam(value = "kind", required = false) String kind,
                                                HttpServletRequest request) {
        permissionService.require(currentUser(request), DmsPermissionPoint.DMS_SCHEMA_VIEW);
        requireRegistered(id);
        return ApiResult.ok(schemaExplorer.listTables(id, schema, kind));
    }

    /** 表结构（字段 + 索引 + 建表语句）。 */
    @GetMapping("/{id}/table")
    public ApiResult<?> tableDetail(@PathVariable("id") String id,
                                    @RequestParam("schema") String schema,
                                    @RequestParam("table") String table,
                                    HttpServletRequest request) {
        permissionService.require(currentUser(request), DmsPermissionPoint.DMS_SCHEMA_VIEW);
        requireRegistered(id);
        return ApiResult.ok(schemaExplorer.describeTable(id, schema, table));
    }

    /**
     * 表数据分页（筛选 + 排序；防注入三件套：表名元数据解析、列名白名单、参数化过滤值）。
     */
    @PostMapping("/{id}/rows")
    public ApiResult<?> rows(@PathVariable("id") String id,
                             @RequestBody TableQuery query,
                             HttpServletRequest request) {
        permissionService.require(currentUser(request), DmsPermissionPoint.DMS_DATA_VIEW);
        requireRegistered(id);
        return ApiResult.ok(tableDataReader.read(id, query));
    }

    private DmsCurrentUser currentUser(HttpServletRequest request) {
        return permissionService.currentUser(request);
    }

    private void requireRegistered(String id) {
        if (dataSourceService.find(id) == null) {
            throw new IllegalArgumentException("数据源未登记: " + id);
        }
    }
}
