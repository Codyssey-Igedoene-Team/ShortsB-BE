-- CSV의 방향성 대비 후보 45건: 로컬 시연용 AI 발췌 검토안
-- 실행 자체가 아래 구절·설명에 대한 사용자의 로컬 시연 승인을 의미
-- 사람의 전체 본문 검수·작품 전체 성격 검증 완료를 뜻하지 않음
-- 외부 작품 ID 사용. 기존 approved/rejected는 보존하고 pending만 갱신
-- 현재 본문 해시·승인 상태·페이지·정확한 구절을 모두 확인한 뒤 일괄 반영
-- 근거의 줄바꿈은 E 문자열의 \n 표기로 고정. Windows 파일 CRLF와 구분
-- book_contrast_relations 스키마/003 적용 및 도서 등록 이후 pgAdmin에서 전체 실행
-- 중간 실패 시 ROLLBACK 실행 후 원인 확인. DB·본문·기존 기록 삭제 없음

BEGIN;
SET LOCAL lock_timeout = '5s';
SELECT pg_advisory_xact_lock(1936220738, 2);
LOCK TABLE public.books, public.book_pages IN SHARE MODE;
LOCK TABLE public.book_contrast_relations IN SHARE ROW EXCLUSIVE MODE;

-- 이번 등록안만 보관하는 세션 임시 테이블
CREATE TEMP TABLE shortb_contrast_seed (
    source_external_id text NOT NULL, -- 기준 작품 외부 ID
    target_external_id text NOT NULL, -- 대상 작품 외부 ID
    axis text NOT NULL, -- 대비 축
    common_topic text NOT NULL, -- 구절 사이의 연결 주제
    reason text NOT NULL, -- 확인한 구간에 한정한 대비 설명
    source_sha256 text NOT NULL, -- 기준 작품의 예상 전체 해시
    target_sha256 text NOT NULL, -- 대상 작품의 예상 전체 해시
    source_page integer NOT NULL, -- 기준 근거 한 페이지
    target_page integer NOT NULL, -- 대상 근거 한 페이지
    source_quote text NOT NULL, -- 기준 페이지의 정확한 짧은 구절
    target_quote text NOT NULL, -- 대상 페이지의 정확한 짧은 구절
    PRIMARY KEY (source_external_id, target_external_id, axis)
) ON COMMIT DROP;

INSERT INTO shortb_contrast_seed VALUES
-- 메밀꽃 필 무렵 7쪽 → 동백꽃 11쪽 (texture)
('buckwheat',
 'camellia',
 'texture',
 '사랑을 느끼는 순간',
 '기준 구절은 젊은 시절 단 한 번의 인연을 돌아보는 회고의 결을, 대상 구절은 꽃향기 속에서 감각이 아찔해지는 현재의 생생한 결을 보여준다.',
 'dc4c4ff33c0191d36badf013dc88f73b5a6399461c3a1955a38c3a79c5ac4ea2',
 'edabafd6a13adc02d7e81cc486c1c32846dad10d1a9b7ac77ce2bd8baff694bb',
 7,
 11,
 '뒤에도 처음에도 없는 단 한번의 괴이한 인연! 봉평에 다니기 시작한 젊은 시절의 일이었으나 그것을 생각할 적만은',
 '알싸한, 그리고 향긋한 그 냄새에 나는 땅이 꺼지는 듯이 온 정신이 고만 아찔하였다.'),

-- 메밀꽃 필 무렵 6쪽 → 봄봄 1쪽 (perspective)
('buckwheat',
 'spring-spring',
 'perspective',
 '일하며 살아가는 사람',
 '기준 구절은 허 생원의 오랜 장돌림 생활을 외부에서 서술하고, 대상 구절은 품삯 없이 일한 시간을 당사자가 직접 호소한다.',
 'dc4c4ff33c0191d36badf013dc88f73b5a6399461c3a1955a38c3a79c5ac4ea2',
 '1893bc6b2bd58a7ff3cc212dbab824c9d8ff95f08e34ba4f4c3d568e4ad6d3a6',
 6,
 1,
 '드팀전 장돌림을 시작한 지 이십 년이나 되어도 허 생원은 봉평 장을 빼논 적은 드물었다.',
 '내가 여기에 와서 돈 한 푼 안 받고 일하기를 삼 년 하고 꼬바기 일곱 달 동안을 했다.'),

-- 메밀꽃 필 무렵 6쪽 → 백야 3쪽 (setting)
('buckwheat',
 'white-nights',
 'setting',
 '익숙한 장소와 정서적 유대',
 '기준 구절의 배경은 장에서 장으로 이어지는 강산이며, 대상 구절의 배경은 노인을 마주치는 도시의 폰탄카이다.',
 'dc4c4ff33c0191d36badf013dc88f73b5a6399461c3a1955a38c3a79c5ac4ea2',
 'dbda6e519df6ca036ce4ab72f1de54434930603e1380fb5b6e3796a00637aaf0',
 6,
 3,
 '장에서 장으로 가는 길의 아름다운 강산이 그대로 그에게는 그리운 고향이었다.',
 '날이면 날마다 같은 시각 폰탄카에서 마주치는 한 노인과는 거의 우정을 맺었다고 할 수 있다.'),

-- 크리스마스 선물 10쪽 → 운수 좋은 날 12쪽 (texture)
('gift-of-magi',
 'lucky-day',
 'texture',
 '가까운 사람을 둘러싼 감정',
 '기준 구절은 상대의 선물을 위해 시계를 판 마음을 전하고, 대상 구절은 고함 뒤의 무시무시한 불안을 드러내어 서로 다른 정서적 결을 만든다.',
 '0c3ab766e7286ae8191e599d4278e12f9427a1cc29dd7b23bc27f2f1ef462396',
 '38569537471c9b67f441607fecb3cbe2368997f6a5146216c370b498a8e9e32d',
 10,
 12,
 '당신 빗을 살 돈을 마련하느라 시계를 팔았어요.',
 '이 고함이야말로 제 몸을 엄습해오는 무시무시한 증을 쫓아버리려는 허장성세(虛張聲勢)인 까닭이다.'),

-- 크리스마스 선물 10쪽 → 봄봄 1쪽 (attitude)
('gift-of-magi',
 'spring-spring',
 'attitude',
 '관계에서 치르는 대가',
 '기준 구절은 상대에게 줄 선물을 위해 자기 시계를 파는 자발적 희생을, 대상 구절은 돈 한 푼 없이 장기간 일한 부담을 호소한다.',
 '0c3ab766e7286ae8191e599d4278e12f9427a1cc29dd7b23bc27f2f1ef462396',
 '1893bc6b2bd58a7ff3cc212dbab824c9d8ff95f08e34ba4f4c3d568e4ad6d3a6',
 10,
 1,
 '당신 빗을 살 돈을 마련하느라 시계를 팔았어요.',
 '내가 여기에 와서 돈 한 푼 안 받고 일하기를 삼 년 하고 꼬바기 일곱 달 동안을 했다.'),

-- 크리스마스 선물 10쪽 → 백야 93쪽 (ending)
('gift-of-magi',
 'white-nights',
 'ending',
 '사랑의 경험에 부여하는 가치',
 '기준의 마지막 페이지는 선물을 주는 두 사람을 지혜롭다고 함께 기리고, 대상의 마지막 페이지는 한순간의 행복을 일생에 견주어 되묻는다.',
 '0c3ab766e7286ae8191e599d4278e12f9427a1cc29dd7b23bc27f2f1ef462396',
 'dbda6e519df6ca036ce4ab72f1de54434930603e1380fb5b6e3796a00637aaf0',
 10,
 93,
 '그러나 요즘의 지혜롭다는 이들에게 마지막으로 한마디 하자면, 선물을 주는 모든 사람 가운데 이 두 사람이 가장 지혜로웠다고 말해 두고 싶다.',
 '하느님, 온전한 행복의 한순간이라니! 그것이 한 사람의 일생 전체에 비해 너무나 작은 것인가?'),

-- 백야 3쪽 → B사감과 러브레터 1쪽 (texture)
('white-nights',
 'headmistress-b',
 'texture',
 '타인을 바라보는 정서',
 '기준 구절은 낯선 사람 사이의 따뜻한 관심을, 대상 구절은 타인을 위축시키는 냉랭하고 위압적인 인물 묘사를 보여준다.',
 'dbda6e519df6ca036ce4ab72f1de54434930603e1380fb5b6e3796a00637aaf0',
 'f9fbe538a415af075b3d52d385e6cacd805e41bb131236d749a3e9ac9fac36a9',
 3,
 1,
 '그도 나를 알아보고 내게 따뜻한 관심을 보인다.',
 '뾰족한 입을 앙다물고 돋보기 너머로 쌀쌀한 눈이 노릴 때엔 기숙생들이 오싹하고 몸서리를 치리만큼 그는 엄격하고 매서웠다.'),

-- 백야 3쪽 → 기다리다 1쪽 (setting)
('white-nights',
 'waiting',
 'setting',
 '낯선 사람과의 만남',
 '기준 구절은 도시의 폰탄카에서 반복해 마주치는 사람을, 대상 구절은 작은 역에서 누구인지 모를 사람을 기다리는 상황을 그린다.',
 'dbda6e519df6ca036ce4ab72f1de54434930603e1380fb5b6e3796a00637aaf0',
 '2c7d47076dc06525e86aef930d8b8ed731a9ffbc8458a6a01d71f4dc5c338550',
 3,
 1,
 '날이면 날마다 같은 시각 폰탄카에서 마주치는 한 노인과는 거의 우정을 맺었다고 할 수 있다.',
 '국철의 그 작은 역으로, 저는 날마다 사람을 마중하러 갑니다. 누군지도 모르는 사람을 마중하러요.'),

-- 백야 93쪽 → 크리스마스 선물 10쪽 (ending)
('white-nights',
 'gift-of-magi',
 'ending',
 '사랑의 경험에 부여하는 가치',
 '기준의 마지막 페이지는 한순간의 행복을 일생과 견주어 되묻고, 대상의 마지막 페이지는 선물을 주는 두 사람을 가장 지혜롭다고 함께 기린다.',
 'dbda6e519df6ca036ce4ab72f1de54434930603e1380fb5b6e3796a00637aaf0',
 '0c3ab766e7286ae8191e599d4278e12f9427a1cc29dd7b23bc27f2f1ef462396',
 93,
 10,
 '하느님, 온전한 행복의 한순간이라니! 그것이 한 사람의 일생 전체에 비해 너무나 작은 것인가?',
 '그러나 요즘의 지혜롭다는 이들에게 마지막으로 한마디 하자면, 선물을 주는 모든 사람 가운데 이 두 사람이 가장 지혜로웠다고 말해 두고 싶다.'),

-- 황금풍경 4쪽 → 벌거벗은 임금님 5쪽 (texture)
('golden-landscape',
 'emperor-clothes',
 'texture',
 '자신이 타인에게 보이는 모습',
 '기준 구절은 스스로 죄인 같다고 느끼는 부끄러움의 결을, 대상 구절은 보이지 않는 것을 남들은 본다고 여기는 우스꽝스러운 집단 허세의 결을 드러낸다.',
 '5440379b4c4dd7ea4d65ac013a1d8abdc7d646fc8fdc98cc4404727e94d5fd26',
 '544d6d539e85248f8d20cb38e24e671e33892eaae9a2f167ee29c961176b7b11',
 4,
 5,
 '문득 얼굴을 들고 그렇게 느닷없는 질문을 던지던 내 얼굴은, 틀림없는 죄인, 피고의 얼굴이었다. 비굴한 웃음마저 띠고 있었던 것으로 기억한다.',
 '그들은 그렇게 말하며 텅 빈 틀을 가리켰다. 자기들 말고 다른 사람은 모두 이 정교한 작품을 볼 수 있을 것이라고 생각했기 때문이다.'),

-- 황금풍경 1쪽 → 이기적인 거인 5쪽 (perspective)
('golden-landscape',
 'selfish-giant',
 'perspective',
 '약한 사람을 대하는 행동',
 '기준 구절은 하녀를 괴롭혔던 일을 가해 당사자의 일인칭 회고로 전하고, 대상 구절은 거인이 아이를 조심스럽게 돕는 행동을 외부에서 보여준다.',
 '5440379b4c4dd7ea4d65ac013a1d8abdc7d646fc8fdc98cc4404727e94d5fd26',
 '6673e5f90b7742cf0c1bdd675e66121404636090f7f9533c5358845bc7743d4c',
 1,
 5,
 '나는 어릴 적에 그다지 성품이 좋은 편이 아니었다. 하녀를 괴롭혔다.',
 '거인은 아이 뒤로 살며시 다가가 손으로 아이를 조심스레 들어 나무 위에 올려 주었다.'),

-- 황금풍경 6쪽 → 치숙 20쪽 (ending)
('golden-landscape',
 'chisu',
 'ending',
 '타인을 인정하는가',
 '기준의 마지막 구절은 타인의 승리를 자기 새 출발의 빛으로 받아들이고, 대상의 마지막 페이지 구절은 상대를 시치미 떼는 사람으로 몰아붙이는 비난을 이어간다.',
 '5440379b4c4dd7ea4d65ac013a1d8abdc7d646fc8fdc98cc4404727e94d5fd26',
 '7da7644e7f55a402574bb8303376692d5428be2779af18f0f2a23f8014b74b59',
 6,
 20,
 '졌다. 이것은 좋은 일이다. 그래야만 하는 것이다. 그들의 승리는, 또한 내일 내가 새로 출발하는 데에도 빛을 준다.',
 '글쎄 이 한다는 소리 좀 보지요? 시치미 뚜욱 따고 누워서 바쁘다는군요!'),

-- 이기적인 거인 5쪽 → 프랑켄슈타인 332쪽 (texture)
('selfish-giant',
 'frankenstein',
 'texture',
 '행동과 고통의 정서',
 '기준 구절은 어린아이를 조심스레 돌보는 온기를, 대상 구절은 비참함과 자기 화장을 말하는 격렬하고 파괴적인 고통을 보여준다.',
 '6673e5f90b7742cf0c1bdd675e66121404636090f7f9533c5358845bc7743d4c',
 'b32585cd5b91088f06ebfee288cdf46aec3e2417b3a17f22864a212136cba145',
 5,
 332,
 '거인은 아이 뒤로 살며시 다가가 손으로 아이를 조심스레 들어 나무 위에 올려 주었다.',
 '곧 이 불타는 비참함은 꺼질 것이오. 나는 의기양양하게 내 화장용 장작더미에 올라, 고문하는 불길의 고통 속에서 환희를 누리겠소.'),

-- 이기적인 거인 5쪽 → 벌거벗은 임금님 5쪽 (tone)
('selfish-giant',
 'emperor-clothes',
 'tone',
 '변화와 체면을 서술하는 말투',
 '기준 구절은 정원을 내어 주고 담을 허무는 변화를 직접적이고 긍정적인 문장으로, 대상 구절은 텅 빈 틀을 그럴듯하게 가리키는 행동을 반어적인 상황 묘사로 전한다.',
 '6673e5f90b7742cf0c1bdd675e66121404636090f7f9533c5358845bc7743d4c',
 '544d6d539e85248f8d20cb38e24e671e33892eaae9a2f167ee29c961176b7b11',
 5,
 5,
 '“얘들아, 이제 이 정원은 너희 것이란다.” 거인은 이렇게 말하고 커다란 도끼를 가져와 담을 허물었다.',
 '그들은 그렇게 말하며 텅 빈 틀을 가리켰다. 자기들 말고 다른 사람은 모두 이 정교한 작품을 볼 수 있을 것이라고 생각했기 때문이다.'),

-- 이기적인 거인 5쪽 → 황금풍경 1쪽 (perspective)
('selfish-giant',
 'golden-landscape',
 'perspective',
 '약한 사람을 대하는 행동',
 '기준 구절은 거인이 아이를 돕는 행동을 외부에서 보여주고, 대상 구절은 하녀를 괴롭힌 일을 당사자의 일인칭 회고로 전한다.',
 '6673e5f90b7742cf0c1bdd675e66121404636090f7f9533c5358845bc7743d4c',
 '5440379b4c4dd7ea4d65ac013a1d8abdc7d646fc8fdc98cc4404727e94d5fd26',
 5,
 1,
 '거인은 아이 뒤로 살며시 다가가 손으로 아이를 조심스레 들어 나무 위에 올려 주었다.',
 '나는 어릴 적에 그다지 성품이 좋은 편이 아니었다. 하녀를 괴롭혔다.'),

-- 동백꽃 11쪽 → 메밀꽃 필 무렵 7쪽 (texture)
('camellia',
 'buckwheat',
 'texture',
 '사랑을 느끼는 순간',
 '기준 구절은 꽃향기 속에서 감각이 아찔해지는 생생함을, 대상 구절은 젊은 시절 단 한 번의 인연을 돌이키는 회고의 결을 드러낸다.',
 'edabafd6a13adc02d7e81cc486c1c32846dad10d1a9b7ac77ce2bd8baff694bb',
 'dc4c4ff33c0191d36badf013dc88f73b5a6399461c3a1955a38c3a79c5ac4ea2',
 11,
 7,
 '알싸한, 그리고 향긋한 그 냄새에 나는 땅이 꺼지는 듯이 온 정신이 고만 아찔하였다.',
 '뒤에도 처음에도 없는 단 한번의 괴이한 인연! 봉평에 다니기 시작한 젊은 시절의 일이었으나 그것을 생각할 적만은'),

-- 동백꽃 6쪽 → 기다리다 4쪽 (tone)
('camellia',
 'waiting',
 'tone',
 '관계를 표현하는 목소리',
 '기준 구절은 상대에게 직접 던지는 거친 호칭을, 대상 구절은 누구를 기다리는지 자신에게 묻는 조용한 독백을 사용한다.',
 'edabafd6a13adc02d7e81cc486c1c32846dad10d1a9b7ac77ce2bd8baff694bb',
 '2c7d47076dc06525e86aef930d8b8ed731a9ffbc8458a6a01d71f4dc5c338550',
 6,
 4,
 '이 바보 녀석아!',
 '대체, 나는, 누구를 기다리고 있는 걸까. 뚜렷한 형체를 지닌 것은 아무것도 없다. 그저, 막연할 뿐이다.'),

-- 동백꽃 1쪽 → 백야 3쪽 (perspective)
('camellia',
 'white-nights',
 'perspective',
 '타인과의 관계를 바라보는 시선',
 '기준 구절은 자기 수탉이 쫓기는 편들기와 다툼에서 출발하고, 대상 구절은 타인이 자신에게 보내는 따뜻한 관심을 포착한다.',
 'edabafd6a13adc02d7e81cc486c1c32846dad10d1a9b7ac77ce2bd8baff694bb',
 'dbda6e519df6ca036ce4ab72f1de54434930603e1380fb5b6e3796a00637aaf0',
 1,
 3,
 '오늘도 또 우리 수탉이 막 쫓기었다.',
 '그도 나를 알아보고 내게 따뜻한 관심을 보인다.'),

-- 봄봄 1쪽 → 크리스마스 선물 10쪽 (texture)
('spring-spring',
 'gift-of-magi',
 'texture',
 '관계에서 치르는 대가',
 '기준 구절은 오랜 무급 노동에 대한 답답함을, 대상 구절은 상대의 선물을 마련하려고 시계를 판 마음을 전한다.',
 '1893bc6b2bd58a7ff3cc212dbab824c9d8ff95f08e34ba4f4c3d568e4ad6d3a6',
 '0c3ab766e7286ae8191e599d4278e12f9427a1cc29dd7b23bc27f2f1ef462396',
 1,
 10,
 '내가 여기에 와서 돈 한 푼 안 받고 일하기를 삼 년 하고 꼬바기 일곱 달 동안을 했다.',
 '당신 빗을 살 돈을 마련하느라 시계를 팔았어요.'),

-- 봄봄 7쪽 → 운수 좋은 날 12쪽 (tone)
('spring-spring',
 'lucky-day',
 'tone',
 '갈등을 서술하는 말투',
 '기준 구절은 장인 호칭을 둘러싼 말장난과 잊어버리는 실수를 익살스럽게 전하고, 대상 구절은 고함을 불안을 쫓으려는 허장성세라고 분석한다.',
 '1893bc6b2bd58a7ff3cc212dbab824c9d8ff95f08e34ba4f4c3d568e4ad6d3a6',
 '38569537471c9b67f441607fecb3cbe2368997f6a5146216c370b498a8e9e32d',
 7,
 12,
 '뱀두 뱀이래야 좋으냐구 창피스러우니 남 듣는 데는 제발 빙장님, 빙모님, 하라구 일상 당조심을 받아오면서 난 그것두 자꾸 잊는다.',
 '이 고함이야말로 제 몸을 엄습해오는 무시무시한 증을 쫓아버리려는 허장성세(虛張聲勢)인 까닭이다.'),

-- 봄봄 1쪽 → 메밀꽃 필 무렵 6쪽 (perspective)
('spring-spring',
 'buckwheat',
 'perspective',
 '일하며 살아가는 사람',
 '기준 구절은 품삯 없이 일한 시간을 당사자가 호소하고, 대상 구절은 허 생원의 오랜 장돌림 생활을 외부에서 서술한다.',
 '1893bc6b2bd58a7ff3cc212dbab824c9d8ff95f08e34ba4f4c3d568e4ad6d3a6',
 'dc4c4ff33c0191d36badf013dc88f73b5a6399461c3a1955a38c3a79c5ac4ea2',
 1,
 6,
 '내가 여기에 와서 돈 한 푼 안 받고 일하기를 삼 년 하고 꼬바기 일곱 달 동안을 했다.',
 '드팀전 장돌림을 시작한 지 이십 년이나 되어도 허 생원은 봉평 장을 빼논 적은 드물었다.'),

-- B사감과 러브레터 1쪽 → 백야 3쪽 (texture)
('headmistress-b',
 'white-nights',
 'texture',
 '타인을 바라보는 정서',
 '기준 구절은 타인을 위축시키는 냉랭하고 위압적인 인물을, 대상 구절은 낯선 사람 사이의 따뜻한 관심을 보여준다.',
 'f9fbe538a415af075b3d52d385e6cacd805e41bb131236d749a3e9ac9fac36a9',
 'dbda6e519df6ca036ce4ab72f1de54434930603e1380fb5b6e3796a00637aaf0',
 1,
 3,
 '뾰족한 입을 앙다물고 돋보기 너머로 쌀쌀한 눈이 노릴 때엔 기숙생들이 오싹하고 몸서리를 치리만큼 그는 엄격하고 매서웠다.',
 '그도 나를 알아보고 내게 따뜻한 관심을 보인다.'),

-- B사감과 러브레터 1쪽 → 벌거벗은 임금님 1쪽 (setting)
('headmistress-b',
 'emperor-clothes',
 'setting',
 '권위를 가진 인물',
 '기준 구절의 권위자는 여학교의 교원이자 기숙사 사감이고, 대상 구절의 권위자는 병사를 거느리는 황제이다.',
 'f9fbe538a415af075b3d52d385e6cacd805e41bb131236d749a3e9ac9fac36a9',
 '544d6d539e85248f8d20cb38e24e671e33892eaae9a2f167ee29c961176b7b11',
 1,
 1,
 'C여학교에서 교원 겸 기숙사 사감 노릇을 하는 B여사라면 딱장대요 독신주의자요 찰진 야소군으로 유명하다.',
 '황제는 병사들에 대해서는 조금도 신경 쓰지 않았다.'),

-- B사감과 러브레터 8쪽 → 동백꽃 11쪽 (tone)
('headmistress-b',
 'camellia',
 'tone',
 '호감을 표현하는 장면',
 '기준 구절은 사내 목청을 흉내 내는 인물의 몸짓을 희화적으로 묘사하고, 대상 구절은 꽃향기와 아찔한 감각을 감각적인 문장으로 전한다.',
 'f9fbe538a415af075b3d52d385e6cacd805e41bb131236d749a3e9ac9fac36a9',
 'edabafd6a13adc02d7e81cc486c1c32846dad10d1a9b7ac77ce2bd8baff694bb',
 8,
 11,
 '누구를 끌어당길 듯이 두 팔을 벌리고 안경을 벗은 근시안으로 잔뜩 한 곳을 노리며 그 굴비쪽 같은 얼굴에 말할 수 없이 애원하는 표정을 짓고는 ''키스''를 기다리는 것 같이 입을 쫑긋이 내어민 채 사내의 목청을 내어가면서 아깟말을 중얼거린다.',
 '알싸한, 그리고 향긋한 그 냄새에 나는 땅이 꺼지는 듯이 온 정신이 고만 아찔하였다.'),

-- 벌거벗은 임금님 8쪽 → 인간 실격 5쪽 (texture)
('emperor-clothes',
 'no-longer-human',
 'texture',
 '수치와 노출',
 '기준 구절은 군중의 공개적 외침으로 황제의 체면을 벗기고, 대상 구절은 자신의 부끄러운 생애와 인간 생활에 대한 막막함을 내면의 고백으로 전한다.',
 '544d6d539e85248f8d20cb38e24e671e33892eaae9a2f167ee29c961176b7b11',
 'aa73b7457f2f835317d709d2c9916fa980e82486ca55d8e77cdc3f19beccfb5c',
 8,
 5,
 '“하지만 아무것도 안 입으셨잖아!” 마침내 모든 사람이 외쳤다.',
 E'부끄러운 일 많은 생애를 살아왔습니다.\n\n저는 인간의 생활이라는 것을 도무지 가늠할 수 없습니다.'),

-- 벌거벗은 임금님 5쪽 → 이기적인 거인 5쪽 (tone)
('emperor-clothes',
 'selfish-giant',
 'tone',
 '변화와 체면을 서술하는 말투',
 '기준 구절은 텅 빈 틀을 가리키는 집단 허세를 반어적 상황 묘사로 전하고, 대상 구절은 정원을 내어 주고 담을 허무는 변화를 직접적이고 긍정적으로 전한다.',
 '544d6d539e85248f8d20cb38e24e671e33892eaae9a2f167ee29c961176b7b11',
 '6673e5f90b7742cf0c1bdd675e66121404636090f7f9533c5358845bc7743d4c',
 5,
 5,
 '그들은 그렇게 말하며 텅 빈 틀을 가리켰다. 자기들 말고 다른 사람은 모두 이 정교한 작품을 볼 수 있을 것이라고 생각했기 때문이다.',
 '“얘들아, 이제 이 정원은 너희 것이란다.” 거인은 이렇게 말하고 커다란 도끼를 가져와 담을 허물었다.'),

-- 벌거벗은 임금님 5쪽 → 치숙 1쪽 (perspective)
('emperor-clothes',
 'chisu',
 'perspective',
 '타인의 행동을 평가하는 방식',
 '기준 구절은 관리들의 행동과 믿음을 외부 서술자가 설명하고, 대상 구절은 상대를 비난하는 인물 자신의 구어적 목소리를 직접 들려준다.',
 '544d6d539e85248f8d20cb38e24e671e33892eaae9a2f167ee29c961176b7b11',
 '7da7644e7f55a402574bb8303376692d5428be2779af18f0f2a23f8014b74b59',
 5,
 1,
 '그들은 그렇게 말하며 텅 빈 틀을 가리켰다. 자기들 말고 다른 사람은 모두 이 정교한 작품을 볼 수 있을 것이라고 생각했기 때문이다.',
 '뭐, 말도 마시오. 대체 사람이 어쩌면 글쎄…… 내 원!'),

-- 치숙 11쪽 → 이기적인 거인 5쪽 (texture)
('chisu',
 'selfish-giant',
 'texture',
 '도움이 필요한 사람을 대하는 정서',
 '기준 구절은 병든 사람을 찾아가는 일에 냉담함을 드러내고, 대상 구절은 거인이 아이를 조심스럽게 돕는 온기를 보여준다.',
 '7da7644e7f55a402574bb8303376692d5428be2779af18f0f2a23f8014b74b59',
 '6673e5f90b7742cf0c1bdd675e66121404636090f7f9533c5358845bc7743d4c',
 11,
 5,
 E'사실 아주머니만 아니면 내가 무슨 천주학이라고\n나쁜 병까지 앓는 그 양반을 찾아다니나요. 죽는대도 코도 안 풀어 붙일걸.',
 '거인은 아이 뒤로 살며시 다가가 손으로 아이를 조심스레 들어 나무 위에 올려 주었다.'),

-- 치숙 1쪽 → 운수 좋은 날 7쪽 (perspective)
('chisu',
 'lucky-day',
 'perspective',
 '어려움에 처한 인물을 보는 시선',
 '기준 구절은 상대를 못마땅해하는 화자의 직접적인 평가를, 대상 구절은 김 첨지가 집을 떠올리며 느끼는 초조함에 접근하는 서술을 보여준다.',
 '7da7644e7f55a402574bb8303376692d5428be2779af18f0f2a23f8014b74b59',
 '38569537471c9b67f441607fecb3cbe2368997f6a5146216c370b498a8e9e32d',
 1,
 7,
 '뭐, 말도 마시오. 대체 사람이 어쩌면 글쎄…… 내 원!',
 '집의 광경이 자꾸 눈앞에 어른거리어 인제 요행을 바랄 여유도 없었다.'),

-- 치숙 20쪽 → 봄봄 7쪽 (tone)
('chisu',
 'spring-spring',
 'tone',
 '상대를 못마땅해하는 목소리',
 '기준 구절은 시치미를 뗀다고 단정하며 상대를 비난하고, 대상 구절은 호칭을 둘러싼 말장난과 잊어버리는 실수로 익살을 만든다.',
 '7da7644e7f55a402574bb8303376692d5428be2779af18f0f2a23f8014b74b59',
 '1893bc6b2bd58a7ff3cc212dbab824c9d8ff95f08e34ba4f4c3d568e4ad6d3a6',
 20,
 7,
 '글쎄 이 한다는 소리 좀 보지요? 시치미 뚜욱 따고 누워서 바쁘다는군요!',
 '뱀두 뱀이래야 좋으냐구 창피스러우니 남 듣는 데는 제발 빙장님, 빙모님, 하라구 일상 당조심을 받아오면서 난 그것두 자꾸 잊는다.'),

-- 기다리다 3쪽 → 메밀꽃 필 무렵 6쪽 (texture)
('waiting',
 'buckwheat',
 'texture',
 '머무를 곳과 정서적 안식',
 '기준 구절은 누군가 나타나기를 바라면서도 두려워하는 불안을, 대상 구절은 장 사이의 길과 강산을 고향처럼 느끼는 친밀함을 보여준다.',
 '2c7d47076dc06525e86aef930d8b8ed731a9ffbc8458a6a01d71f4dc5c338550',
 'dc4c4ff33c0191d36badf013dc88f73b5a6399461c3a1955a38c3a79c5ac4ea2',
 3,
 6,
 '누군가, 불쑥 나타난다면! 하는 기대와, 아아, 나타나면 곤란해, 어떻게 하지 하는 두려움과,',
 '장에서 장으로 가는 길의 아름다운 강산이 그대로 그에게는 그리운 고향이었다.'),

-- 기다리다 4쪽 → 동백꽃 6쪽 (tone)
('waiting',
 'camellia',
 'tone',
 '관계를 표현하는 목소리',
 '기준 구절은 누구를 기다리는지 자신에게 묻는 조용한 독백을, 대상 구절은 상대에게 직접 던지는 거친 호칭을 사용한다.',
 '2c7d47076dc06525e86aef930d8b8ed731a9ffbc8458a6a01d71f4dc5c338550',
 'edabafd6a13adc02d7e81cc486c1c32846dad10d1a9b7ac77ce2bd8baff694bb',
 4,
 6,
 '대체, 나는, 누구를 기다리고 있는 걸까. 뚜렷한 형체를 지닌 것은 아무것도 없다. 그저, 막연할 뿐이다.',
 '이 바보 녀석아!'),

-- 기다리다 3쪽 → 지하로부터의 수기 3쪽 (attitude)
('waiting',
 'underground',
 'attitude',
 '자기 안의 모순을 대하는 태도',
 '기준 구절은 만남에 대한 기대와 두려움을 함께 드러내고, 대상 구절은 의사를 찾아가지 않는 이유를 자기 심술이라고 단정한다.',
 '2c7d47076dc06525e86aef930d8b8ed731a9ffbc8458a6a01d71f4dc5c338550',
 '255fd389605d9b7f24622c04259af7d3ae8585f832a8b644df41b8036425aece',
 3,
 3,
 '누군가, 불쑥 나타난다면! 하는 기대와, 아아, 나타나면 곤란해, 어떻게 하지 하는 두려움과,',
 '아니, 내가 의사를 찾아가지 않는 것은 심술 때문이다.'),

-- 운수 좋은 날 12쪽 → 크리스마스 선물 10쪽 (texture)
('lucky-day',
 'gift-of-magi',
 'texture',
 '가까운 사람을 둘러싼 감정',
 '기준 구절은 고함 뒤에 숨은 무시무시한 불안을, 대상 구절은 상대의 선물을 마련하려고 시계를 판 마음을 드러낸다.',
 '38569537471c9b67f441607fecb3cbe2368997f6a5146216c370b498a8e9e32d',
 '0c3ab766e7286ae8191e599d4278e12f9427a1cc29dd7b23bc27f2f1ef462396',
 12,
 10,
 '이 고함이야말로 제 몸을 엄습해오는 무시무시한 증을 쫓아버리려는 허장성세(虛張聲勢)인 까닭이다.',
 '당신 빗을 살 돈을 마련하느라 시계를 팔았어요.'),

-- 운수 좋은 날 12쪽 → 봄봄 7쪽 (tone)
('lucky-day',
 'spring-spring',
 'tone',
 '갈등을 서술하는 말투',
 '기준 구절은 고함을 불안을 쫓으려는 허장성세라고 분석하고, 대상 구절은 호칭을 둘러싼 말장난과 실수를 익살스럽게 전한다.',
 '38569537471c9b67f441607fecb3cbe2368997f6a5146216c370b498a8e9e32d',
 '1893bc6b2bd58a7ff3cc212dbab824c9d8ff95f08e34ba4f4c3d568e4ad6d3a6',
 12,
 7,
 '이 고함이야말로 제 몸을 엄습해오는 무시무시한 증을 쫓아버리려는 허장성세(虛張聲勢)인 까닭이다.',
 '뱀두 뱀이래야 좋으냐구 창피스러우니 남 듣는 데는 제발 빙장님, 빙모님, 하라구 일상 당조심을 받아오면서 난 그것두 자꾸 잊는다.'),

-- 운수 좋은 날 1쪽 → 인간 실격 5쪽 (setting)
('lucky-day',
 'no-longer-human',
 'setting',
 '생활을 경험하는 공간',
 '기준 구절은 동소문 안에서 일하는 인력거꾼의 도시 생활을, 대상 구절은 도호쿠 시골에서 자라 기차를 늦게 접한 경험을 배경으로 삼는다.',
 '38569537471c9b67f441607fecb3cbe2368997f6a5146216c370b498a8e9e32d',
 'aa73b7457f2f835317d709d2c9916fa980e82486ca55d8e77cdc3f19beccfb5c',
 1,
 5,
 '이날이야말로 동소문 안에서 인력거꾼 노릇을 하는 김 첨지에게는 오래간만에도 닥친 운수 좋은 날이었다.',
 '저는 도호쿠의 시골에서 태어났기 때문에, 기차를 처음 본 것은 꽤 자란 뒤였습니다.'),

-- 지하로부터의 수기 3쪽 → B사감과 러브레터 8쪽 (texture)
('underground',
 'headmistress-b',
 'texture',
 '타인 앞에 드러나는 내면',
 '기준 구절은 병과 심술을 스스로 단정하는 차갑고 공격적인 자기 묘사를, 대상 구절은 사랑을 갈구하는 인물의 몸짓을 희화적으로 드러낸다.',
 '255fd389605d9b7f24622c04259af7d3ae8585f832a8b644df41b8036425aece',
 'f9fbe538a415af075b3d52d385e6cacd805e41bb131236d749a3e9ac9fac36a9',
 3,
 8,
 '나는 병든 인간이다…… 나는 심술궂은 인간이다. 나는 매력 없는 인간이다.',
 '누구를 끌어당길 듯이 두 팔을 벌리고 안경을 벗은 근시안으로 잔뜩 한 곳을 노리며 그 굴비쪽 같은 얼굴에 말할 수 없이 애원하는 표정을 짓고는 ''키스''를 기다리는 것 같이 입을 쫑긋이 내어민 채 사내의 목청을 내어가면서 아깟말을 중얼거린다.'),

-- 지하로부터의 수기 3쪽 → 인간 실격 5쪽 (tone)
('underground',
 'no-longer-human',
 'tone',
 '자신을 설명하는 목소리',
 '기준 구절은 병든 인간이며 심술궂다고 짧게 단정하는 거친 말투를, 대상 구절은 부끄러운 생애와 이해하지 못하는 인간 생활을 정중한 고백으로 전한다.',
 '255fd389605d9b7f24622c04259af7d3ae8585f832a8b644df41b8036425aece',
 'aa73b7457f2f835317d709d2c9916fa980e82486ca55d8e77cdc3f19beccfb5c',
 3,
 5,
 '나는 병든 인간이다…… 나는 심술궂은 인간이다. 나는 매력 없는 인간이다.',
 E'부끄러운 일 많은 생애를 살아왔습니다.\n\n저는 인간의 생활이라는 것을 도무지 가늠할 수 없습니다.'),

-- 지하로부터의 수기 193쪽 → 황금풍경 6쪽 (ending)
('underground',
 'golden-landscape',
 'ending',
 '자신과 삶을 받아들이는 방식',
 '기준의 마지막 페이지는 인간이라는 사실을 수치로 느끼는 진술을 남기고, 대상의 마지막 구절은 타인의 승리에서 자기 새 출발의 빛을 찾는다.',
 '255fd389605d9b7f24622c04259af7d3ae8585f832a8b644df41b8036425aece',
 '5440379b4c4dd7ea4d65ac013a1d8abdc7d646fc8fdc98cc4404727e94d5fd26',
 193,
 6,
 '우리는 인간이라는 사실, 실제로 각자의 몸과 피를 지닌 인간이라는 사실에 짓눌린다. 그것을 부끄러워하고 수치로 여기며, 어떤 불가능한 일반화된 인간이 되려고 궁리한다.',
 '졌다. 이것은 좋은 일이다. 그래야만 하는 것이다. 그들의 승리는, 또한 내일 내가 새로 출발하는 데에도 빛을 준다.'),

-- 인간 실격 5쪽 → 벌거벗은 임금님 8쪽 (texture)
('no-longer-human',
 'emperor-clothes',
 'texture',
 '수치와 노출',
 '기준 구절은 자기 생애의 부끄러움을 내면의 고백으로 전하고, 대상 구절은 군중의 외침으로 황제의 체면이 공개적으로 벗겨지는 장면을 보여준다.',
 'aa73b7457f2f835317d709d2c9916fa980e82486ca55d8e77cdc3f19beccfb5c',
 '544d6d539e85248f8d20cb38e24e671e33892eaae9a2f167ee29c961176b7b11',
 5,
 8,
 E'부끄러운 일 많은 생애를 살아왔습니다.\n\n저는 인간의 생활이라는 것을 도무지 가늠할 수 없습니다.',
 '“하지만 아무것도 안 입으셨잖아!” 마침내 모든 사람이 외쳤다.'),

-- 인간 실격 5쪽 → 지하로부터의 수기 3쪽 (tone)
('no-longer-human',
 'underground',
 'tone',
 '자신을 설명하는 목소리',
 '기준 구절은 자신의 막막함을 정중한 고백으로 전하고, 대상 구절은 병과 심술을 짧고 거친 문장으로 단정한다.',
 'aa73b7457f2f835317d709d2c9916fa980e82486ca55d8e77cdc3f19beccfb5c',
 '255fd389605d9b7f24622c04259af7d3ae8585f832a8b644df41b8036425aece',
 5,
 3,
 E'부끄러운 일 많은 생애를 살아왔습니다.\n\n저는 인간의 생활이라는 것을 도무지 가늠할 수 없습니다.',
 '나는 병든 인간이다…… 나는 심술궂은 인간이다. 나는 매력 없는 인간이다.'),

-- 인간 실격 5쪽 → 프랑켄슈타인 3쪽 (attitude)
('no-longer-human',
 'frankenstein',
 'attitude',
 '삶과 미지의 경험을 대하는 태도',
 '기준 구절은 인간의 생활을 도무지 가늠할 수 없다는 막막함을, 대상 구절은 위험과 죽음의 두려움을 넘어서 항해하려는 의욕을 드러낸다.',
 'aa73b7457f2f835317d709d2c9916fa980e82486ca55d8e77cdc3f19beccfb5c',
 'b32585cd5b91088f06ebfee288cdf46aec3e2417b3a17f22864a212136cba145',
 5,
 3,
 E'부끄러운 일 많은 생애를 살아왔습니다.\n\n저는 인간의 생활이라는 것을 도무지 가늠할 수 없습니다.',
 '이런 것들이 저를 유혹합니다. 위험이나 죽음에 대한 온갖 두려움을 이겨 내고 이 고된 항해를 시작하게 하기에 충분합니다.'),

-- 프랑켄슈타인 332쪽 → 이기적인 거인 5쪽 (texture)
('frankenstein',
 'selfish-giant',
 'texture',
 '행동과 고통의 정서',
 '기준 구절은 자기 화장을 말하는 격렬하고 파괴적인 고통을, 대상 구절은 어린아이를 조심스럽게 돌보는 온기를 보여준다.',
 'b32585cd5b91088f06ebfee288cdf46aec3e2417b3a17f22864a212136cba145',
 '6673e5f90b7742cf0c1bdd675e66121404636090f7f9533c5358845bc7743d4c',
 332,
 5,
 '곧 이 불타는 비참함은 꺼질 것이오. 나는 의기양양하게 내 화장용 장작더미에 올라, 고문하는 불길의 고통 속에서 환희를 누리겠소.',
 '거인은 아이 뒤로 살며시 다가가 손으로 아이를 조심스레 들어 나무 위에 올려 주었다.'),

-- 프랑켄슈타인 3쪽 → 지하로부터의 수기 3쪽 (perspective)
('frankenstein',
 'underground',
 'perspective',
 '일인칭 화자의 관심 방향',
 '기준 구절의 일인칭 화자는 위험을 넘어 외부 세계로 나아갈 이유에 주목하고, 대상 구절의 일인칭 화자는 자신의 병과 심술을 열거하며 내면으로 시선을 돌린다.',
 'b32585cd5b91088f06ebfee288cdf46aec3e2417b3a17f22864a212136cba145',
 '255fd389605d9b7f24622c04259af7d3ae8585f832a8b644df41b8036425aece',
 3,
 3,
 '이런 것들이 저를 유혹합니다. 위험이나 죽음에 대한 온갖 두려움을 이겨 내고 이 고된 항해를 시작하게 하기에 충분합니다.',
 '나는 병든 인간이다…… 나는 심술궂은 인간이다. 나는 매력 없는 인간이다.'),

-- 프랑켄슈타인 3쪽 → 인간 실격 5쪽 (attitude)
('frankenstein',
 'no-longer-human',
 'attitude',
 '삶과 미지의 경험을 대하는 태도',
 '기준 구절은 두려움을 넘어서 고된 항해를 시작할 의욕을, 대상 구절은 인간의 생활을 도무지 가늠하지 못하는 막막함을 드러낸다.',
 'b32585cd5b91088f06ebfee288cdf46aec3e2417b3a17f22864a212136cba145',
 'aa73b7457f2f835317d709d2c9916fa980e82486ca55d8e77cdc3f19beccfb5c',
 3,
 5,
 '이런 것들이 저를 유혹합니다. 위험이나 죽음에 대한 온갖 두려움을 이겨 내고 이 고된 항해를 시작하게 하기에 충분합니다.',
 E'부끄러운 일 많은 생애를 살아왔습니다.\n\n저는 인간의 생활이라는 것을 도무지 가늠할 수 없습니다.');

-- 하나라도 다르면 부분 승인 없이 트랜잭션 전체 중단
DO $validation$
DECLARE
    invalid_count integer;
    invalid_details text;
BEGIN
    SELECT count(*), string_agg(
        format('%s -> %s (%s): %s',
               x.source_external_id, x.target_external_id, x.axis,
               concat_ws(', ',
                   CASE WHEN s.id IS NULL THEN '기준 도서 없음' END,
                   CASE WHEN t.id IS NULL THEN '대상 도서 없음' END,
                   CASE WHEN s.id = t.id THEN '동일 도서' END,
                   CASE WHEN s.content_rights IS DISTINCT FROM 'approved' THEN '기준 도서 미승인' END,
                   CASE WHEN t.content_rights IS DISTINCT FROM 'approved' THEN '대상 도서 미승인' END,
                   CASE WHEN s.text_sha256 IS DISTINCT FROM x.source_sha256 THEN '기준 본문 해시' END,
                   CASE WHEN t.text_sha256 IS DISTINCT FROM x.target_sha256 THEN '대상 본문 해시' END,
                   CASE WHEN sp.book_id IS NULL THEN '기준 페이지 없음' END,
                   CASE WHEN tp.book_id IS NULL THEN '대상 페이지 없음' END,
                   CASE WHEN strpos(sp.content, x.source_quote) = 0 THEN '기준 구절 불일치' END,
                   CASE WHEN strpos(tp.content, x.target_quote) = 0 THEN '대상 구절 불일치' END)),
        E'\n' ORDER BY x.source_external_id, x.target_external_id, x.axis
    ) INTO invalid_count, invalid_details
    FROM shortb_contrast_seed x
    LEFT JOIN public.books s ON s.external_id = x.source_external_id
    LEFT JOIN public.books t ON t.external_id = x.target_external_id
    LEFT JOIN public.book_pages sp ON sp.book_id = s.id AND sp.page_no = x.source_page
    LEFT JOIN public.book_pages tp ON tp.book_id = t.id AND tp.page_no = x.target_page
    WHERE s.id IS NULL OR t.id IS NULL OR s.id = t.id
       OR s.content_rights IS DISTINCT FROM 'approved'
       OR t.content_rights IS DISTINCT FROM 'approved'
       OR s.text_sha256 IS DISTINCT FROM x.source_sha256
       OR t.text_sha256 IS DISTINCT FROM x.target_sha256
       OR sp.book_id IS NULL OR tp.book_id IS NULL
       OR strpos(sp.content, x.source_quote) = 0
       OR strpos(tp.content, x.target_quote) = 0;
    IF invalid_count > 0 THEN
        RAISE EXCEPTION '대비 등록 중단: 도서 승인·본문 해시·페이지·구절 불일치 %건', invalid_count
            USING DETAIL = invalid_details;
    END IF;
END
$validation$;

-- 원본 ID를 DB ID로 연결. 기존 명시적 승인·제외 결과는 변경하지 않음
WITH registered AS (
    INSERT INTO public.book_contrast_relations AS existing (
        source_book_id, target_book_id, axis, common_topic, reason,
        source_content_sha256, target_content_sha256,
        source_start_page_no, source_end_page_no, source_evidence,
        target_start_page_no, target_end_page_no, target_evidence,
        review_status, reviewed_by, reviewed_at
    )
    SELECT s.id, t.id, x.axis, x.common_topic, x.reason,
           x.source_sha256, x.target_sha256,
           x.source_page, x.source_page, x.source_quote,
           x.target_page, x.target_page, x.target_quote,
           'approved', 'local-demo-user:AI-assisted', statement_timestamp()
    FROM shortb_contrast_seed x
    JOIN public.books s ON s.external_id = x.source_external_id
    JOIN public.books t ON t.external_id = x.target_external_id
    WHERE true
    ON CONFLICT (source_book_id, target_book_id, axis)
    DO UPDATE SET
        common_topic = EXCLUDED.common_topic,
        reason = EXCLUDED.reason,
        source_content_sha256 = EXCLUDED.source_content_sha256,
        target_content_sha256 = EXCLUDED.target_content_sha256,
        source_start_page_no = EXCLUDED.source_start_page_no,
        source_end_page_no = EXCLUDED.source_end_page_no,
        source_evidence = EXCLUDED.source_evidence,
        target_start_page_no = EXCLUDED.target_start_page_no,
        target_end_page_no = EXCLUDED.target_end_page_no,
        target_evidence = EXCLUDED.target_evidence,
        review_status = EXCLUDED.review_status,
        reviewed_by = EXCLUDED.reviewed_by,
        reviewed_at = EXCLUDED.reviewed_at,
        updated_at = statement_timestamp()
    WHERE existing.review_status = 'pending'
    RETURNING id
)
SELECT count(*) AS inserted_or_pending_updated FROM registered;

-- 이번 45개 후보에 한정한 승인/제외 현황. 기존 제외 관계는 0건으로 숨기지 않음
SELECT r.review_status, count(*) AS relation_count
FROM shortb_contrast_seed x
JOIN public.books s ON s.external_id = x.source_external_id
JOIN public.books t ON t.external_id = x.target_external_id
JOIN public.book_contrast_relations r
  ON r.source_book_id = s.id AND r.target_book_id = t.id AND r.axis = x.axis
GROUP BY r.review_status ORDER BY r.review_status;

-- 실제 사용할 페이지 범위와 등록 당시 본문 버전·권한 확인. 본문 구절 출력 없음
SELECT s.title AS source_title, t.title AS target_title, r.axis,
       r.source_start_page_no AS source_evidence_page,
       r.target_start_page_no AS target_evidence_page,
       r.review_status,
       (s.content_rights = 'approved' AND t.content_rights = 'approved'
        AND r.source_content_sha256 = s.text_sha256
        AND r.target_content_sha256 = t.text_sha256) AS content_ready
FROM shortb_contrast_seed x
JOIN public.books s ON s.external_id = x.source_external_id
JOIN public.books t ON t.external_id = x.target_external_id
JOIN public.book_contrast_relations r
  ON r.source_book_id = s.id AND r.target_book_id = t.id AND r.axis = x.axis
ORDER BY s.title, t.title, r.axis;

COMMIT;
