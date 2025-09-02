package br.gov.pb.der.netnotify.utils;

import java.util.ArrayList;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

public class PageWrapper<T> {

    public static final int MAX_PAGE_ITEM_DISPLAY = 5;
    public static final int MAX_ITENS_DISPLAY = 15;
    private Page<T> page;
    //private List<PageItem> items;
    private final int currentNumber;
    private String url;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public PageWrapper(Page<T> page, String url) {
        this.page = page;
        this.url = url;
        currentNumber = page.getNumber() + 1; // start from 1 to match page.page
    }

    public PageWrapper(String url) {
        currentNumber = 1;
        this.url = url;
        this.page = new PageImpl<>(new ArrayList<>());
    }

    public int getNumber() {
        return currentNumber;
    }

    public Page<T> getPage() {
        return page;
    }

    public void setPage(Page<T> page) {
        this.page = page;
    }

}
