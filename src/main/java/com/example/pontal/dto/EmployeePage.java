package com.example.pontal.dto;

import java.util.List;

//一覧表示の要素を持つクラス(社員サマリー配列,全件数,次のページ番号,前のページ番号)
public class EmployeePage {
    private List<EmployeeSummary> items;
    private Integer totalCount;
    private Integer next;
    private Integer prev;

    public List<EmployeeSummary> getItems() { return items; }
    public void setItems(List<EmployeeSummary> items) { this.items = items; }

    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }

    public Integer getNext() { return next; }
    public void setNext(Integer next) { this.next = next; }

    public Integer getPrev() { return prev; }
    public void setPrev(Integer prev) { this.prev = prev; }
}
