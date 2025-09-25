package br.gov.pb.der.netnotify.filter;

public abstract class AbstractFilter {

    private boolean isPaginated = true;
    private Integer page;
    private Integer offset;
    private Integer size;

    public static final int SORTBYMETHOD_ASC = 1;
    public static final int SORTBYMETHOD_DESC = 2;

    protected int sortBy;
    protected int sortOrder;

    public AbstractFilter() {
        super();
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer limit) {
        this.size = limit;
    }

    public Integer getOffset() {
        return offset;
    }

    public void setOffset(Integer offset) {
        this.offset = offset;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer currentPage) {
        this.page = currentPage;
    }

    public boolean isPaginated() {
        return isPaginated;
    }

    public void setPaginated(boolean isPaginated) {
        this.isPaginated = isPaginated;
    }

    public int getSortBy() {
        return sortBy;
    }

    public void setSortBy(int sortBy) {
        this.sortBy = sortBy;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortByMethod) {
        this.sortOrder = sortByMethod;
    }

    public static int getSortByMethodAsc() {
        return SORTBYMETHOD_ASC;
    }

    public static int getSortByMethodDesc() {
        return SORTBYMETHOD_DESC;
    }

}
