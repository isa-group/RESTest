from root.data.dataset import read_dataset


SERVICES = [
    "GitHub", 
    # "Stripe_Coupons", 
    "Stripe_Products", 
    "Yelp_Businesses", 
    "YouTube_CommentsAndThreads", 
    "YouTube_Videos", 
    "YouTube_Search", 
]

REPETITIONS = 1

for i in range(REPETITIONS):
    for service in SERVICES:

        data = read_dataset('../../target/test-data/'+service+'_'+str(i), '../src/test/resources/'+service+'/props.properties')

        print('\n'+ service + '_' + str(i))
        print(data.size)
        print(data.obt_valid_ratio)