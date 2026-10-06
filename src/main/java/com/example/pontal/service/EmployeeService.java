package com.example.pontal.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.pontal.dto.EmployeeCreateRequest;
import com.example.pontal.dto.EmployeeDetail;
import com.example.pontal.dto.EmployeePage;
import com.example.pontal.dto.EmployeeSummary;
import com.example.pontal.dto.EmployeeUpdateRequest;
import com.example.pontal.dto.LoginEmployee;
import com.example.pontal.exception.UnauthorizedException;
import com.example.pontal.mapper.EmployeeMapper;

import com.example.pontal.exception.ValidationException;
import com.example.pontal.exception.ForbiddenException;
import com.example.pontal.exception.NotFoundException;
import com.example.pontal.exception.ConflictException;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDisableUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AliasExistsException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidPasswordException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;


@Service
public class EmployeeService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeService.class);

    private final EmployeeMapper employeeMapper;
    private final CognitoIdentityProviderClient cognitoClient;

    //CognitoユーザープールID(.envのCOGNITO_USER_POOL_IDが実体)
    @Value("${cognito.user-pool-id}")
    private String userPoolId;

    public EmployeeService(EmployeeMapper employeeMapper, CognitoIdentityProviderClient cognitoClient) {
        this.employeeMapper = employeeMapper;
        this.cognitoClient = cognitoClient;
    }

    //JWTのsubからログイン社員を解決する(他のServiceからも認可判定に使う)
    public LoginEmployee getLoginEmployee(String cognitoSub) {
        LoginEmployee employee = employeeMapper.findByCognitoSub(cognitoSub);

        //JWTは有効だがemployeeに居ない/論理削除済み → ログイン不可(401)
        if (employee == null) {
            throw new UnauthorizedException("employee not found");
        }
        return employee;
    }

        //社員詳細を返す(ログイン済みなら誰でも閲覧可)
    public EmployeeDetail getDetail(Long id) {
        EmployeeDetail detail = employeeMapper.findDetailById(id);

        //存在しない/論理削除済み → 404
        if (detail == null) {
            throw new NotFoundException("社員が見つかりません");
        }
        return detail;
    }

        //社員一覧を返す(ログイン済みなら誰でも閲覧可)
    public EmployeePage search(int page, int size, String keyword) {
        //Notionのバリデーション規約: page=0以上、size=1〜100
        if (page < 0) {
            throw new ValidationException("pageは0以上で指定してください");
        }
        if (size < 1 || size > 100) {
            throw new ValidationException("sizeは1〜100で指定してください");
        }

        long offset = (long) page * size; //何件目から取るか
        List<EmployeeSummary> items = employeeMapper.selectByKeyword(keyword, size, offset);
        int totalCount = employeeMapper.countByKeyword(keyword);

        EmployeePage result = new EmployeePage();
        result.setItems(items);
        result.setTotalCount(totalCount);
        //次のページがあれば番号、無ければnull
        result.setNext(offset + size < totalCount ? page + 1 : null);
        //実在する最終ページ番号(該当なし=0件のときは0)
        int lastPage = Math.max(0, (totalCount + size - 1) / size - 1);
        //2ページ目以降なら前のページ番号(範囲外のページが指定されても最終ページまでに丸める)、先頭ならnull
        result.setPrev(page > 0 ? Math.min(page - 1, lastPage) : null);
        return result;
    }

        //社員情報を編集して更新後の詳細を返す(システム管理者のみ)
    public EmployeeDetail update(String cognitoSub, Long id, EmployeeUpdateRequest request) {
        LoginEmployee login = getLoginEmployee(cognitoSub);

        //権限チェックを先にする(権限が無い人に、社員が存在するかを教えないため)
        if (!login.getIsSystemAdmin()) {
            throw new ForbiddenException("社員を編集する権限がありません");
        }

        int updated = employeeMapper.update(id, request);

        //更新0件 → 存在しない/論理削除済み
        if (updated == 0) {
            throw new NotFoundException("社員が見つかりません");
        }
        return getDetail(id);
    }

    //社員を登録する(システム管理者のみ)。Cognitoにログイン用ユーザーを作り、そのsubと一緒にDBへ登録する
    public EmployeeDetail create(String cognitoSub, EmployeeCreateRequest request) {
        LoginEmployee login = getLoginEmployee(cognitoSub);

        if (!login.getIsSystemAdmin()) {
            throw new ForbiddenException("社員を登録する権限がありません");
        }

        //DB側の重複を先に確認(Cognitoにゴミのユーザーを作らないため)
        if (employeeMapper.countByEmail(request.getEmail()) > 0) {
            throw new ConflictException("このメールアドレスは既に登録されています");
        }

        //ユーザープールはメールをユーザー名として使う設定のため、メールアドレスをそのまま渡す
        //(Cognitoが内部でsub(UUID)を発行する)
        String username = request.getEmail();

        AdminCreateUserRequest createRequest = AdminCreateUserRequest.builder()
                .userPoolId(userPoolId)
                .username(username)
                .temporaryPassword(request.getInitialPassword())
                .userAttributes(
                        AttributeType.builder().name("email").value(request.getEmail()).build(),
                        //メールでのログインに使うため検証済みにしておく
                        AttributeType.builder().name("email_verified").value("true").build())
                .messageAction(MessageActionType.SUPPRESS) //招待メールは送らない
                .build();

        AdminCreateUserResponse created;
        try {
            created = cognitoClient.adminCreateUser(createRequest);
        } catch (UsernameExistsException | AliasExistsException e) {
            throw new ConflictException("このメールアドレスは既に登録されています");
        } catch (InvalidPasswordException e) {
            throw new ValidationException("初期パスワードがパスワードポリシーを満たしていません");
        }

        //Cognitoが発行したsub(employee.cognito_subに保存する値)を取り出す
        String newSub = created.user().attributes().stream()
                .filter(a -> a.name().equals("sub"))
                .findFirst()
                .orElseThrow()
                .value();

        try {
            Long id = employeeMapper.insert(request, newSub);
            return getDetail(id);
        } catch (RuntimeException e) {
            //DB登録に失敗したら、作ったCognitoユーザーも消して不整合を残さない
            cognitoClient.adminDeleteUser(AdminDeleteUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(username)
                    .build());
            throw e;
        }
    }

    //社員を論理削除する(システム管理者のみ、自分自身は削除不可)
    public void delete(String cognitoSub, Long id) {
        LoginEmployee login = getLoginEmployee(cognitoSub);

        //権限チェックを先にする(権限が無い人に、社員が存在するかを教えないため)
        if (!login.getIsSystemAdmin()) {
            throw new ForbiddenException("社員を削除する権限がありません");
        }

        //自分自身を削除すると、管理者が居なくなる事故につながるため禁止
        if (login.getEmployeeId().equals(id)) {
            throw new ForbiddenException("自分自身は削除できません");
        }

        //更新0件 → 存在しない/削除済み
        if (employeeMapper.softDeleteById(id) == 0) {
            throw new NotFoundException("社員が見つかりません");
        }

        //DBの削除が成功してから、Cognito側のユーザーも無効化する(二重の安全策)
        disableCognitoUser(employeeMapper.findCognitoSubById(id));
    }

    //Cognitoのユーザーを無効化する(トークンの新規発行を止める)。
    //ログイン可否はDBのis_deleteで既に止まっているため、失敗しても削除自体は成功として扱い、ログだけ残す
    private void disableCognitoUser(String cognitoSub) {
        try {
            cognitoClient.adminDisableUser(AdminDisableUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(cognitoSub)
                    .build());
        } catch (SdkException e) {
            //Cognitoに存在しないユーザー(ダミーデータなど)や、通信エラーもここに入る
            log.warn("Cognitoユーザーの無効化に失敗しました sub={}", cognitoSub, e);
        }
    }

}
