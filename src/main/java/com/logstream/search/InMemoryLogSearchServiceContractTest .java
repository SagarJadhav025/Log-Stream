package com.logstream.search;

import java.util.List;

import com.LongStream.search.LogSearchServiceContractTest;


class InMemoryLogSearchServiceContractTest extends LogSearchServiceContractTest {

    @Override
    protected InMemoryLogSearchService create() {
        return new InMemoryLogSearchService(1000);
    }

    @Override
    protected void assertEquals(int of, int ids) {
      
        throw new UnsupportedOperationException("Unimplemented method 'assertEquals'");
    }

    @Override
    protected void assertEquals(List<String> of, List<String> ids) {
    
        throw new UnsupportedOperationException("Unimplemented method 'assertEquals'");
    }

    @Override
    protected void assertTrue(boolean empty) {
       
        throw new UnsupportedOperationException("Unimplemented method 'assertTrue'");
    }
}
