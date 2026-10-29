package com.example.pontal.dto;

import java.util.List;

//お知らせ一覧の1ページ分(お知らせ配列,全件数,次のページ番号,前のページ番号)
public class AnnouncementPage {
    private List<AnnouncementItem> items;
    private Integer totalCount;
    private Integer next;
    private Integer prev;

    public List<AnnouncementItem> getItems() { return items; }
    public void setItems(List<AnnouncementItem> items) { this.items = items; }

    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }

    public Integer getNext() { return next; }
    public void setNext(Integer next) { this.next = next; }

    public Integer getPrev() { return prev; }
    public void setPrev(Integer prev) { this.prev = prev; }
}
