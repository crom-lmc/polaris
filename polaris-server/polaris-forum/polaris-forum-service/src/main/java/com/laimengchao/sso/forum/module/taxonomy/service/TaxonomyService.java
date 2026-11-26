package com.laimengchao.sso.forum.module.taxonomy.service;


import com.laimengchao.sso.forum.module.taxonomy.dto.BoardRequest;
import com.laimengchao.sso.forum.module.taxonomy.dto.TagCreateRequest;
import com.laimengchao.sso.forum.module.taxonomy.dto.TagUpdateRequest;
import com.laimengchao.sso.forum.module.taxonomy.entity.Board;
import com.laimengchao.sso.forum.module.taxonomy.vo.BoardVO;
import com.laimengchao.sso.forum.module.taxonomy.vo.TagVO;
import java.util.List;


public interface TaxonomyService {

    List<BoardVO> boards();

    List<TagVO> tags(String boardKey);

    List<BoardVO> adminBoards();

    List<TagVO> adminTags(String boardKey);

    BoardVO createBoard(BoardRequest body);

    BoardVO updateBoard(String key, BoardRequest body);

    TagVO createTag(TagCreateRequest body);

    TagVO updateTag(long id, TagUpdateRequest body);

    Board requireBoard(String boardKey, boolean enabledOnly);

    void registerTags(String boardKey, List<String> tags);

    void preparePublishTags(String boardKey, List<String> tags);
}
