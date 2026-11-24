package org.javaup.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.javaup.entity.OrderItem;

import java.util.List;

public interface OrderItemMapper extends BaseMapper<OrderItem> {

    /**
     * 批量插入订单明细（单条 SQL，避免下单时逐座位 INSERT 产生 N 次数据库往返）
     */
    @Insert("<script>" +
            "INSERT INTO order_item (id, order_id, schedule_seat_id, seat_code, row_no, col_no, price, created_at) VALUES " +
            "<foreach collection='items' item='i' separator=','>" +
            "(#{i.id}, #{i.orderId}, #{i.scheduleSeatId}, #{i.seatCode}, #{i.rowNo}, #{i.colNo}, #{i.price}, #{i.createdAt})" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("items") List<OrderItem> items);
}
