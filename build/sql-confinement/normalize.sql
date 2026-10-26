CREATE FUNCTION dbo.AiDataNormalizeV1(@query nvarchar(max)) RETURNS nvarchar(max)
AS BEGIN
 DECLARE @length int=DATALENGTH(@query)/2;
 IF @query IS NULL OR @length NOT BETWEEN 3 AND 100 RETURN NULL;
 DECLARE @chars table(Pos int PRIMARY KEY,NextPos int,Codepoint int,Category int,Whitespace bit,LetterDigit bit,Cased bit,LowerText nvarchar(4));
 DECLARE @p int=1,@cp int,@low int,@next int,@cat int,@white bit,@letter bit,@cased bit,@text nvarchar(4);
 WHILE @p<=@length BEGIN
  SET @cp=UNICODE(SUBSTRING(@query COLLATE Latin1_General_100_BIN2,@p,1)); SET @next=@p+1;
  IF @cp BETWEEN 55296 AND 56319 BEGIN
   SET @low=UNICODE(SUBSTRING(@query COLLATE Latin1_General_100_BIN2,@next,1));
   IF @low IS NULL OR @low NOT BETWEEN 56320 AND 57343 RETURN NULL;
   SET @cp=65536+(@cp-55296)*1024+@low-56320; SET @next=@next+1;
  END ELSE IF @cp BETWEEN 56320 AND 57343 RETURN NULL;
  SELECT @cat=Category,@white=Whitespace,@letter=LetterDigit,@cased=Cased FROM dbo.AiDataUnicodeRange WHERE StartCodepoint<=@cp AND EndCodepoint>=@cp;
  SET @text=NULL; SELECT @text=LowerText FROM dbo.AiDataUnicodeLower WHERE Codepoint=@cp;
  INSERT @chars VALUES(@p,@next,@cp,@cat,@white,@letter,@cased,COALESCE(@text,SUBSTRING(@query COLLATE Latin1_General_100_BIN2,@p,@next-@p)));
  SET @p=@next;
 END;
 IF EXISTS(SELECT 1 FROM @chars WHERE (Pos=1 OR NextPos=@length+1) AND Whitespace=1) OR (SELECT COUNT(*) FROM @chars WHERE LetterDigit=1)<3 RETURN NULL;
 -- Exact forward JDK RuleBasedBreakIterator.handleNext; UTF-16 positions are one based here.
 DECLARE @bounds table(Pos int PRIMARY KEY); INSERT @bounds VALUES(1);
 DECLARE @start int=1,@result int,@look int,@state int,@end bit,@ahead bit;
 WHILE @start<=@length BEGIN
  SELECT @result=NextPos FROM @chars WHERE Pos=@start;
  SET @look=0; SET @state=1; SET @p=@start;
  WHILE @p<=@length AND @state<>0 BEGIN
   SELECT @cp=Codepoint,@cat=Category,@next=NextPos FROM @chars WHERE Pos=@p;
   IF @cp=65535 BREAK;
   IF @cat<>-1 SELECT @state=NextState FROM dbo.AiDataWordTransition WHERE StateId=@state AND Category=@cat;
   -- Ignore-category transitions retain the current state's end/lookahead flags.
   SELECT TOP(1) @end=IsEnd,@ahead=Lookahead FROM dbo.AiDataWordTransition WHERE NextState=@state;
   IF @ahead=1 BEGIN IF @end=1 SET @result=@look; ELSE SET @look=@next; END
   ELSE IF @end=1 SET @result=@next;
   SET @p=@next;
  END;
  IF (@p>@length OR @cp=65535) AND @look=@length+1 SET @result=@look;
  IF @result<=@start RETURN NULL;
  INSERT @bounds VALUES(@result); SET @start=@result;
 END;
 DECLARE @output nvarchar(max)=N'',@left int,@right int;
 SET @p=1;
 WHILE @p<=@length BEGIN
  SELECT @cp=Codepoint,@next=NextPos,@text=LowerText FROM @chars WHERE Pos=@p;
  IF @cp=931 BEGIN
   SELECT @left=MAX(Pos) FROM @bounds WHERE Pos<=@p;
   SELECT @right=MIN(Pos) FROM @bounds WHERE Pos>@p;
   IF EXISTS(SELECT 1 FROM @chars WHERE Pos>=@left AND Pos<@p AND Cased=1)
    AND NOT EXISTS(SELECT 1 FROM @chars WHERE Pos>@p AND Pos<@right AND Cased=1) SET @text=NCHAR(962);
  END;
  SET @output=@output+@text; SET @p=@next;
 END;
 RETURN N'%'+REPLACE(REPLACE(REPLACE(@output,N'[',N'[[]'),N'%',N'[%]'),N'_',N'[_]')+N'%';
END;
