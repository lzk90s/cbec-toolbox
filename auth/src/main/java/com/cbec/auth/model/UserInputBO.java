package com.cbec.auth.model;

import com.cbec.auth.dao.entity.UserEntity;
import com.cbec.common.valication.group.Create;
import com.google.common.base.Converter;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import javax.validation.constraints.NotNull;

/**
 * @author liuzhikun
 */
@Data
public class UserInputBO {
    @NotNull(message = "用户id不能为空", groups = {Create.class})
    private Long id;
    private String phone;
    private Boolean state;

    public static class ConverterImpl extends Converter<UserInputBO, UserEntity> {

        @Override
        public UserEntity doForward(UserInputBO userInputBO) {
            UserEntity userEntity = new UserEntity();
            BeanUtils.copyProperties(userInputBO, userEntity);
            return userEntity;
        }

        @Override
        protected UserInputBO doBackward(UserEntity userEntity) {
            throw new UnsupportedOperationException();
        }
    }
}
