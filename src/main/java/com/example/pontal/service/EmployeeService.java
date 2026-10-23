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
import com.example.pontal.exception.ExternalServiceException;

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
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;


@Service
public class EmployeeService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeService.class);

    private final EmployeeMapper employeeMapper;
    private final CognitoIdentityProviderClient cognitoClient;

    //CognitoユーザープールID(.envのCOGNITO_USER_POOL_IDが実体)
    @Value("${cognito.user-pool-id}")
    private String userPoolId;
    //コンストラクタでDIにより部品を受け取る
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
    public EmployeePage list(int page, int size, String keyword) {
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

        int updatedCount = employeeMapper.update(id, request);

        //更新0件 → 存在しない/論理削除済み
        if (updatedCount == 0) {
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
        //Cognitoへのユーザー登録リクエストを作成
        AdminCreateUserRequest createRequest = AdminCreateUserRequest.builder()
                .userPoolId(userPoolId)                          //ユーザープールID
                .username(username)                              //ユーザーネーム(email)
                .temporaryPassword(request.getInitialPassword()) //パスワード
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

        //ここから先でDBへの登録が終わるまでに失敗したら、作ったCognitoユーザーを消して、不整合を残さない
        //(tryに入れるのは「subの取り出し」と「INSERT」だけ。INSERTが成功した後の失敗では、DBに行が残るのでCognitoは消さない)
        Long id;
        try {
            //Cognitoが発行したsub(employee.cognito_subに保存する値)を取り出す
            String newSub = created.user().attributes().stream()
                    .filter(a -> a.name().equals("sub"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Cognitoの応答にsubが含まれていません"))
                    .value();
            id = employeeMapper.insert(request, newSub);
        } catch (RuntimeException e) {
            deleteCognitoUser(username, e);
            throw e;
        }

        //登録した社員を読み直して返す(失敗しても、DBには登録済みなのでCognitoは消さない)
        return getDetail(id);
    }

    //登録の途中で失敗したときに、作ったCognitoユーザーを消す(巻き戻し)
    //消すのに失敗しても、もともと起きたエラー(original)を隠さないように、付け足して残す
    private void deleteCognitoUser(String username, RuntimeException original) {
        try {
            cognitoClient.adminDeleteUser(AdminDeleteUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(username)
                    .build());
        } catch (SdkException deleteError) {
            original.addSuppressed(deleteError);
            log.error("Cognitoユーザーの巻き戻し(削除)に失敗しました。手動で削除が必要です username={}", username, deleteError);
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

    //Cognitoのユーザーを無効化する(トークンの新規発行を止める)
    private void disableCognitoUser(String cognitoSub) {
        try {
            cognitoClient.adminDisableUser(AdminDisableUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(cognitoSub)
                    .build());
        } catch (UserNotFoundException e) {
            //Cognitoにユーザーがいない(ダミーデータなど)。無効化する相手がいないので、成功扱いにする
            log.warn("Cognitoにユーザーが存在しないため、無効化をスキップしました sub={}", cognitoSub);
        } catch (SdkException e) {
            //権限エラーや通信エラーなど。本物のユーザーがCognitoで有効なまま残るため、成功扱いにしない
            //DBの論理削除は済んでいる(APIは使えない)ので、管理者にエラーで知らせ、手動で無効化してもらう
            log.error("Cognitoユーザーの無効化に失敗しました sub={}", cognitoSub, e);
            throw new ExternalServiceException(
                    "社員は削除されましたが、Cognitoの無効化に失敗しました。Cognitoコンソールで手動で無効化してください");
        }
    }

}
