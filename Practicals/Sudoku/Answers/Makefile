all: Sudoku.class

clean:
	rm *.class; fsc -shutdown

GraphSearch.class: TerminatingPartialStack.class

ConcGraphSearch.class: GraphSearch.class

Sudoku.class: ConcGraphSearch.class GraphSearch.class Partial.class 

FSC = fsc -cp .:/home/gavin/Scala/SCL


%.class:	%.scala
	$(FSC) $<
