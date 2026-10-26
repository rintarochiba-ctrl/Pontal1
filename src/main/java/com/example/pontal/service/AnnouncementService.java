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

// お知らせのServiceクラス
// 権限(誰が作成・編集・削除できるか)と入力チェックをここで行い、DBの操作は Mapper に任せる
//   作成・編集 : システム管理者・HR管理者
//   削除       : システム管理者のみ
//   閲覧       : 全員向け + 自分宛て + 自分の部署宛て(管理者は全件)
@Service
public class AnnouncementService {
    private final AnnouncementMapper announcementMapper;
    private final EmployeeMapper employeeMapper;
    // JWTのsubからログイン中の社員を調べるのに使う
    private final EmployeeService employeeService;

    // お知らせの種類として指定できる値
    private static final List<String> CATEGORIES = List.of("技術", "社内情報", "対応依頼");
    // 一覧の scope として指定できる値
    //   all=自分が見られるもの全部 / mine=自分宛て(個人・部署) / everyone=全員向けだけ / manage=全件(管理者のみ)
    private static final List<String> SCOPES = List.of("all", "mine", "manage", "everyone");
    // 宛先の種類として指定できる値(ALL=全員 / DEPARTMENT=部署 / EMPLOYEE=個人)
    private static final List<String> TARGET_TYPES = List.of("ALL", "DEPARTMENT", "EMPLOYEE");

    // コンストラクタでDIにより部品を受け取る
    public AnnouncementService(AnnouncementMapper announcementMapper, EmployeeMapper employeeMapper,
            EmployeeService employeeService) {
        this.announcementMapper = announcementMapper;
        this.employeeMapper = employeeMapper;
        this.employeeService = employeeService;
    }

    // 宛先に選べる部署の一覧を返す(API023)
    public List<String> getDepartments() {
        return employeeMapper.selectDepartments();
    }

    // ---------- 一覧(API018) ----------

    // お知らせ一覧を返す
    // scope=manage(全件)は管理者だけが使える。それ以外の scope は誰でも使えて、見られるお知らせだけが返る
    public AnnouncementPage list(String cognitoSub, String scope, String category, int page, int size) {
        // 指定された値が正しいかを確認する(category は絞り込みなので、指定がなければ確認しない)
        validateListParams(scope, page, size);
        if (category != null) {
            validateCategory(category);
        }
        LoginEmployee login = employeeService.getLoginEmployee(cognitoSub);

        // manage は管理者だけ(それ以外の scope は、一般社員も使える)
        if (scope.equals("manage") && !canManage(login)) {
            throw new ForbiddenException("権限がありません");
        }

        // 「自分の部署宛て」を探すために、ログイン中の人の部署が要る
        String department = departmentOf(login);
        // 何件目から取るか(例: 1ページ目 page=0 なら 0件目から、2ページ目 page=1 で size=10 なら 10件目から)
        long offset = (long) page * size;

        // 見られるお知らせを1ページ分と、見られるお知らせの全件数をDBから取る
        List<Announcement> rows = announcementMapper.selectVisible(scope, login.getEmployeeId(), department, category,
                size, offset);
        int totalCount = announcementMapper.countVisible(scope, login.getEmployeeId(), department, category);

        // DBの行を、一覧の1件分(本文なし・宛先の表示名つき)に変える
        List<AnnouncementItem> items = new ArrayList<>();
        for (Announcement row : rows) {
            items.add(toItem(row));
        }

        AnnouncementPage result = new AnnouncementPage();
        result.setItems(items);
        result.setTotalCount(totalCount);
        // 次のページがあるなら、その番号。なければ null
        result.setNext(offset + size < totalCount ? page + 1 : null);
        // 実際にある最終ページの番号(0件のときは0)
        int lastPage = Math.max(0, (totalCount + size - 1) / size - 1);
        // 2ページ目以降なら前のページの番号。先頭なら null
        // 範囲外のページ(最終ページより後ろ)が指定されても、前のページは最終ページまでに収める
        result.setPrev(page > 0 ? Math.min(page - 1, lastPage) : null);
        return result;
    }

    // ---------- 詳細(API019) ----------

    // お知らせの詳細(本文つき)を返す
    // 見られない人・存在しない・削除済みは、どれも404にする(見られない人に、存在を教えないため)
    public AnnouncementDetail get(String cognitoSub, Long id) {
        LoginEmployee login = employeeService.getLoginEmployee(cognitoSub);

        // 管理者は宛先を問わず全件、それ以外の人は自分が見られるものだけ
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

        // 権限の確認を、入力の確認より先にする(権限がない人に、入力のどこが誤りかを教えないため)
        if (!canManage(login)) {
            throw new ForbiddenException("お知らせを投稿する権限がありません");
        }
        validateRequest(request);

        // 差出人の部署は、投稿した人の部署を、投稿した時点でコピーして保存する
        // (あとで本人が異動しても、過去のお知らせの差出人は変わらない)
        String senderDepartment = departmentOf(login);
        if (senderDepartment == null || senderDepartment.isBlank()) {
            throw new ValidationException("部署が未設定のため、お知らせを投稿できません");
        }

        Announcement announcement = toEntity(request);
        announcement.setSenderEmployeeId(login.getEmployeeId());
        announcement.setSenderDepartment(senderDepartment);
        Long id = announcementMapper.insert(announcement);

        // 登録した内容をDBから読み直して返す(個人宛ての宛先の社員名などを、詳細に入れるため)
        return toDetail(announcementMapper.findById(id), login);
    }

    // ---------- 編集(API021) ----------

    // お知らせを編集して、編集後の詳細を返す(システム管理者・HR管理者のみ)
    // 宛先も変えられる。差出人と作成日時は変わらない
    public AnnouncementDetail update(String cognitoSub, Long id, AnnouncementRequest request) {
        LoginEmployee login = employeeService.getLoginEmployee(cognitoSub);

        // 権限 → 存在 → 入力の順に確認する(先に確認するものほど、見ず知らずの人にも教えてよい情報にする)
        if (!canManage(login)) {
            throw new ForbiddenException("お知らせを編集する権限がありません");
        }
        if (announcementMapper.findById(id) == null) {
            throw new NotFoundException("お知らせが見つかりません");
        }
        validateRequest(request);

        // 存在を確認した直後に、ほかの人に削除されることもあるので、更新できた行数も確認する
        if (announcementMapper.update(id, toEntity(request)) == 0) {
            throw new NotFoundException("お知らせが見つかりません");
        }
        return toDetail(announcementMapper.findById(id), login);
    }

    // ---------- 削除(API022) ----------

    // お知らせを削除する(論理削除。システム管理者のみ)
    public void delete(String cognitoSub, Long id) {
        LoginEmployee login = employeeService.getLoginEmployee(cognitoSub);

        // 権限の確認を、存在の確認より先にする(権限がない人に、お知らせがあるかを教えないため)
        if (!canDelete(login)) {
            throw new ForbiddenException("お知らせを削除する権限がありません");
        }

        // 削除した行数が返る。0件なら、削除する相手がいなかった(存在しない、または削除済み)ということ
        int deleted = announcementMapper.softDeleteById(id);
        if (deleted == 0) {
            throw new NotFoundException("お知らせが見つかりません");
        }
    }

    // ---------- 共通の部品 ----------

    // 作成・編集・全件の閲覧(scope=manage)ができる人か(システム管理者・HR管理者)
    private boolean canManage(LoginEmployee login) {
        return login.getIsSystemAdmin() || login.getIsHrAdmin();
    }

    // 削除ができる人か(システム管理者のみ)
    private boolean canDelete(LoginEmployee login) {
        return login.getIsSystemAdmin();
    }

    // ログイン中の社員の部署を返す
    // LoginEmployee には部署が入っていないので、社員情報から調べる。部署が未設定、または社員が見つからなければ null
    private String departmentOf(LoginEmployee login) {
        EmployeeDetail employee = employeeMapper.findDetailById(login.getEmployeeId());
        return employee == null ? null : employee.getDepartment();
    }

    // 一覧・詳細に表示する宛先の名前を返す(全員向けは「全員」、部署宛ては部署名、個人宛ては社員名)
    private String targetLabelOf(Announcement a) {
        if ("DEPARTMENT".equals(a.getTargetType())) {
            return a.getTargetDepartment();
        }
        if ("EMPLOYEE".equals(a.getTargetType())) {
            return a.getTargetEmployeeName();
        }
        return "全員";
    }

    // DBのお知らせ → 一覧の1件分(本文は含めない)
    private AnnouncementItem toItem(Announcement a) {
        AnnouncementItem item = new AnnouncementItem();
        fillItem(item, a);
        return item;
    }

    // DBのお知らせ → 詳細(一覧の項目に、本文・更新日時と、ログイン中の人が編集・削除できるかを足す)
    private AnnouncementDetail toDetail(Announcement a, LoginEmployee login) {
        AnnouncementDetail detail = new AnnouncementDetail();
        fillItem(detail, a);
        detail.setBody(a.getBody());
        detail.setUpdatedAt(a.getUpdatedAt());
        detail.setCanEdit(canManage(login));
        detail.setCanDelete(canDelete(login));
        return detail;
    }

    // 一覧と詳細に共通の項目を、DBのお知らせから写す
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

    // リクエスト → DBに保存する形のお知らせ(差出人は、作成のときに別で入れる)
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

    // 種類が、決められた選択肢のどれかであることを確認する
    private void validateCategory(String category) {
        if (category == null || !CATEGORIES.contains(category)) {
            throw new ValidationException("種類が正しくありません");
        }
    }

    // 一覧の指定(scope は選択肢のどれか、page は0以上、size は1〜100)を確認する
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

    // 作成・編集のリクエストの内容を確認する(タイトルは100文字以内、本文は2000文字以内、種類、宛先)
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

    // 宛先が、宛先の種類と合っていて、実在するかを確認する
    //   ALL        : 部署も社員も指定しない
    //   DEPARTMENT : 部署だけを指定する(その部署に所属する社員がいること)
    //   EMPLOYEE   : 社員だけを指定する(その社員が存在すること)
    private void validateTarget(AnnouncementRequest request) {
        String type = request.getTargetType();
        String department = request.getTargetDepartment();
        Long employeeId = request.getTargetEmployeeId();

        if (type == null || !TARGET_TYPES.contains(type)) {
            throw new ValidationException("宛先の種類が正しくありません");
        }
        if (type.equals("ALL")) {
            if (department != null || employeeId != null) {
                throw new ValidationException("全員向けの場合、部署と社員は指定できません");
            }
        } else if (type.equals("DEPARTMENT")) {
            if (department == null || department.isBlank() || employeeId != null) {
                throw new ValidationException("部署宛ての場合、部署だけを指定してください");
            }
            // 部署のマスタがないため、その部署に所属する社員がいるかで、部署が実在するかを判断する
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
