package com.example.pontal.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.pontal.dto.Announcement;
import com.example.pontal.dto.AnnouncementDetail;
import com.example.pontal.dto.AnnouncementItem;
import com.example.pontal.dto.AnnouncementPage;
import com.example.pontal.dto.AnnouncementRequest;
import com.example.pontal.dto.EmployeeDetail;
import com.example.pontal.dto.LoginEmployee;
import com.example.pontal.exception.ForbiddenException;
import com.example.pontal.exception.NotFoundException;
import com.example.pontal.exception.ValidationException;
import com.example.pontal.mapper.AnnouncementMapper;
import com.example.pontal.mapper.EmployeeMapper;

@Service
public class AnnouncementService {
    // field
    private final AnnouncementMapper announcementMapper;
    private final EmployeeMapper employeeMapper;
    // ログイン中の社員を調べるのに使う(SalarySlipServiceと同じ)
    private final EmployeeService employeeService;

    // お知らせの種類
    private static final List<String> CATEGORIES = List.of("技術", "社内情報", "対応依頼");
    // scopeの種類
    private static final List<String> SCOPES = List.of("all", "mine", "manage", "everyone");

    // 宛先の種類
    private static final List<String> TARGET_TYPES = List.of("ALL", "DEPARTMENT", "EMPLOYEE");

    // constructor
    public AnnouncementService(AnnouncementMapper announcementMapper, EmployeeMapper employeeMapper,
            EmployeeService employeeService) {
        this.announcementMapper = announcementMapper;
        this.employeeMapper = employeeMapper;
        this.employeeService = employeeService;
    }

    // 部署の選択肢(API023)
    public List<String> getDepartments() {
        return employeeMapper.selectDepartments();
    }

    // ---------- 一覧(API018) ----------

    // お知らせ一覧を返す。scope=manageは、管理者(システム管理者・HR管理者)だけが使える
    public AnnouncementPage list(String cognitoSub, String scope, String category, int page, int size) {
        validateListParams(scope, page, size);
        if (category != null) {
            validateCategory(category);
        }
        LoginEmployee login = employeeService.getLoginEmployee(cognitoSub);

        // ① manage は管理者だけ
        if (scope.equals("manage") && !canManage(login)) {
            throw new ForbiddenException("権限がありません");
        }

        String department = departmentOf(login);
        long offset = (long) page * size;

        // ② DBから取る(Mapperの引数: scope, viewerId, viewerDepartment, category, size,
        // offset)
        List<Announcement> rows = announcementMapper.selectVisible(scope, login.getEmployeeId(), department, category,
                size, offset);
        int totalCount = announcementMapper.countVisible(scope, login.getEmployeeId(), department, category);

        // ③ DBの行 → 一覧の1件分に変える(toItem を使う)
        List<AnnouncementItem> items = new ArrayList<>();
        for (Announcement row : rows) {
            items.add(toItem(row));
        }

        // ④ 返す入れ物に詰める
        AnnouncementPage result = new AnnouncementPage();
        result.setItems(items);
        result.setTotalCount(totalCount);
        // 次のページがあれば番号、なければ null
        result.setNext(offset + size < totalCount ? page + 1 : null);
        // 実在する最終ページ番号(0件のときは0)
        int lastPage = Math.max(0, (totalCount + size - 1) / size - 1);
        // 2ページ目以降なら前のページ番号(最終ページを超えないように丸める)、先頭なら null
        result.setPrev(page > 0 ? Math.min(page - 1, lastPage) : null);
        return result;
    }

    // ---------- 詳細(API019) ----------

    // お知らせ詳細を返す。見られない人・存在しない・削除済みは、どれも404(存在を教えないため)
    public AnnouncementDetail get(String cognitoSub, Long id) {
        LoginEmployee login = employeeService.getLoginEmployee(cognitoSub);

        // 管理者は宛先を問わず全件、それ以外は自分に見えるものだけ
        String scope = canManage(login) ? "manage" : "all";
        Announcement found = announcementMapper.findVisibleById(id, scope, login.getEmployeeId(),
                departmentOf(login));
        if (found == null) {
            throw new NotFoundException("お知らせが見つかりません");
        }
        return toDetail(found, login);
    }

    // ---------- 作成(API020) ----------

    // お知らせを作成して、作成後の詳細を返す(システム管理者・HR管理者のみ)
    public AnnouncementDetail create(String cognitoSub, AnnouncementRequest request) {
        LoginEmployee login = employeeService.getLoginEmployee(cognitoSub);

        // 権限チェックを先にする(権限が無い人に、入力の良し悪しを教えないため)
        if (!canManage(login)) {
            throw new ForbiddenException("お知らせを投稿する権限がありません");
        }
        validateRequest(request);

        // 差出人の部署は、投稿した人の部署を、その時点でコピーして保存する(異動しても、お知らせは変わらない)
        String senderDepartment = departmentOf(login);
        if (senderDepartment == null || senderDepartment.isBlank()) {
            throw new ValidationException("部署が未設定のため、お知らせを投稿できません");
        }

        Announcement announcement = toEntity(request);
        announcement.setSenderEmployeeId(login.getEmployeeId());
        announcement.setSenderDepartment(senderDepartment);
        Long id = announcementMapper.insert(announcement);

        // 登録した内容を読み直して返す(宛先の社員名なども入れるため)
        return toDetail(announcementMapper.findById(id), login);
    }

    // ---------- 編集(API021) ----------

    // お知らせを編集して、編集後の詳細を返す(システム管理者・HR管理者のみ)。宛先も変えられるが、差出人は変わらない
    public AnnouncementDetail update(String cognitoSub, Long id, AnnouncementRequest request) {
        LoginEmployee login = employeeService.getLoginEmployee(cognitoSub);

        // 権限 → 存在 → 入力の順に確認する
        if (!canManage(login)) {
            throw new ForbiddenException("お知らせを編集する権限がありません");
        }
        if (announcementMapper.findById(id) == null) {
            throw new NotFoundException("お知らせが見つかりません");
        }
        validateRequest(request);

        // 確認の直後に、ほかの人に削除されることもあるので、更新した行数も見る
        if (announcementMapper.update(id, toEntity(request)) == 0) {
            throw new NotFoundException("お知らせが見つかりません");
        }
        return toDetail(announcementMapper.findById(id), login);
    }

    // ---------- 削除(API022) ----------

    // お知らせを削除する(論理削除)。システム管理者のみ
    public void delete(String cognitoSub, Long id) {
        LoginEmployee login = employeeService.getLoginEmployee(cognitoSub);

        // ① 削除できるのはシステム管理者だけ(権限が先、存在確認が後)
        if (!canDelete(login)) {
            throw new ForbiddenException("お知らせを削除する権限がありません");
        }

        // ② 論理削除する(戻り値は、更新した行数)
        int deleted = announcementMapper.softDeleteById(id);

        // ③ 0件 → 存在しない、または削除済み
        if (deleted == 0) {
            throw new NotFoundException("お知らせが見つかりません");
        }
    }

    // ---------- 共通の部品 ----------

    // 作成・編集ができる人(システム管理者・HR管理者)。scope=manageを使える人も同じ
    private boolean canManage(LoginEmployee login) {
        return login.getIsSystemAdmin() || login.getIsHrAdmin();
    }

    // 削除できる人(システム管理者のみ)
    private boolean canDelete(LoginEmployee login) {
        return login.getIsSystemAdmin();
    }

    // ログイン中の社員の部署。ログイン情報(LoginEmployee)には部署が入っていないので、社員情報から調べる
    // 部署が未設定、または社員が見つからなければ null
    private String departmentOf(LoginEmployee login) {
        EmployeeDetail employee = employeeMapper.findDetailById(login.getEmployeeId());
        return employee == null ? null : employee.getDepartment();
    }

    // 宛先の表示名(全員 / 部署名 / 宛先の社員名)
    private String targetLabelOf(Announcement a) {
        if ("DEPARTMENT".equals(a.getTargetType())) {
            return a.getTargetDepartment();
        }
        if ("EMPLOYEE".equals(a.getTargetType())) {
            return a.getTargetEmployeeName();
        }
        return "全員";
    }

    // DBのお知らせ → 一覧の1件分
    private AnnouncementItem toItem(Announcement a) {
        AnnouncementItem item = new AnnouncementItem();
        fillItem(item, a);
        return item;
    }

    // DBのお知らせ → 詳細(本文と、ログイン中の人が編集・削除できるかを足す)
    private AnnouncementDetail toDetail(Announcement a, LoginEmployee login) {
        AnnouncementDetail detail = new AnnouncementDetail();
        fillItem(detail, a);
        detail.setBody(a.getBody());
        detail.setUpdatedAt(a.getUpdatedAt());
        detail.setCanEdit(canManage(login));
        detail.setCanDelete(canDelete(login));
        return detail;
    }

    // 一覧と詳細で共通の項目を詰める
    private void fillItem(AnnouncementItem item, Announcement a) {
        item.setId(a.getId());
        item.setTitle(a.getTitle());
        item.setCategory(a.getCategory());
        item.setIsImportant(a.getIsImportant());
        item.setSenderDepartment(a.getSenderDepartment());
        item.setTargetType(a.getTargetType());
        item.setTargetLabel(targetLabelOf(a));
        item.setCreatedAt(a.getCreatedAt());
    }

    // リクエスト → DB用のお知らせ(差出人は、作成のときに別で入れる)
    private Announcement toEntity(AnnouncementRequest request) {
        Announcement a = new Announcement();
        a.setTitle(request.getTitle());
        a.setBody(request.getBody());
        a.setCategory(request.getCategory());
        a.setIsImportant(request.getIsImportant());
        a.setTargetType(request.getTargetType());
        a.setTargetDepartment(request.getTargetDepartment());
        a.setTargetEmployeeId(request.getTargetEmployeeId());
        return a;
    }

    // ---------- 入力チェック ----------

    private void validateCategory(String category) {
        if (category == null || !CATEGORIES.contains(category)) {
            throw new ValidationException("種類が正しくありません");
        }
    }

    private void validateListParams(String scope, int page, int size) {
        if (scope == null || !SCOPES.contains(scope)) {
            throw new ValidationException("scopeが正しくありません");
        }
        if (page < 0) {
            throw new ValidationException("pageは0以上で指定してください");
        }
        if (size < 1 || size > 100) {
            throw new ValidationException("sizeは1〜100で指定してください");
        }
    }

    // 作成・編集のリクエストの内容を確認する
    private void validateRequest(AnnouncementRequest request) {
        String title = request.getTitle();
        if (title == null || title.isBlank() || title.length() > 100) {
            throw new ValidationException("タイトルは1〜100文字で入力してください");
        }
        String body = request.getBody();
        if (body == null || body.isBlank() || body.length() > 2000) {
            throw new ValidationException("本文は1〜2000文字で入力してください");
        }
        validateCategory(request.getCategory());
        validateTarget(request);
    }

    // 宛先が、種類と合っていて、実在するかを確認する
    private void validateTarget(AnnouncementRequest request) {
        String type = request.getTargetType();
        String department = request.getTargetDepartment();
        Long employeeId = request.getTargetEmployeeId();

        if (type == null || !TARGET_TYPES.contains(type)) {
            throw new ValidationException("宛先の種類が正しくありません");
        }
        if (type.equals("ALL")) {
            // 全員向けなのに、部署や社員が指定されている
            if (department != null || employeeId != null) {
                throw new ValidationException("全員向けの場合、部署と社員は指定できません");
            }
        } else if (type.equals("DEPARTMENT")) {
            if (department == null || department.isBlank() || employeeId != null) {
                throw new ValidationException("部署宛ての場合、部署だけを指定してください");
            }
            if (employeeMapper.countByDepartment(department) == 0) {
                throw new ValidationException("指定した部署が存在しません");
            }
        } else {
            if (employeeId == null || department != null) {
                throw new ValidationException("個人宛ての場合、社員だけを指定してください");
            }
            if (employeeMapper.findDetailById(employeeId) == null) {
                throw new ValidationException("指定した社員が存在しません");
            }
        }
    }
}
