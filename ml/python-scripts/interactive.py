from root.data.dataset import read_dataset


SERVICES = [
    "GitHub", 
    "Stripe_Coupons", 
    "Stripe_Products", 
    "Yelp_Businesses", 
    "YouTube_CommentsAndThreads", 
    "YouTube_Videos", 
    "YouTube_Search", 
]

exp = 0

for service in SERVICES:

    data = read_dataset('../../target/test-data/'+service+'_'+str(exp), '../src/test/resources/'+service+'/props.properties')

    print(data.size)
    print(data.obt_valid_ratio)