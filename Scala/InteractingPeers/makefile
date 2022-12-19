DIR = .

all:   $(DIR)/SumTest.class

clean:
	rm $(DIR)/*.class $(DIR)/*/*.class; fsc -shutdown

FSC = fsc -cp .:/home/gavin/Scala/SCL

$(DIR)/SumTest.class: $(DIR)/Sum.class

$(DIR)/%.class:     %.scala
	$(FSC)  $<
